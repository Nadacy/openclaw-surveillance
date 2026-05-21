package com.openclaw.surveillance.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.openclaw.surveillance.detection.FaceDetector
import com.openclaw.surveillance.detection.MotionDetector
import com.openclaw.surveillance.detection.PresenceStateMachine
import com.openclaw.surveillance.detection.SurveillanceState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 双摄像头管理器
 *
 * 后置：主力监控（ImageAnalysis → 帧分析）
 * 前置：辅助确认（ImageAnalysis → 帧分析）
 * 各自独立，互为主备
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onError: (String, String) -> Unit  // cameraId, errorMsg
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    data class CameraConfig(
        val rearEnabled: Boolean = true,
        val frontEnabled: Boolean = false
    )

    data class CameraStatus(
        val cameraId: String,
        val position: String,  // "rear" or "front"
        val active: Boolean,
        val state: String,
        val error: String? = null
    )

    // 各摄像头独立的检测器
    val rearMotionDetector = MotionDetector()
    val frontMotionDetector = MotionDetector()
    val rearFaceDetector = FaceDetector()
    val frontFaceDetector = FaceDetector()

    // 各摄像头独立的状态机
    var rearStateMachine: PresenceStateMachine? = null
    var frontStateMachine: PresenceStateMachine? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var rearCamera: Camera? = null
    private var frontCamera: Camera? = null
    private var rearAnalysis: ImageAnalysis? = null
    private var frontAnalysis: ImageAnalysis? = null
    var rearVideoCapture: VideoCapture<Recorder>? = null
        private set
    var frontVideoCapture: VideoCapture<Recorder>? = null
        private set

    private val cameraExecutor: ExecutorService = Executors.newFixedThreadPool(4)
    var config: CameraConfig = CameraConfig()
        private set

    // 帧回调（Motion → Face → StateMachine）
    var onFrameProcessed: ((cameraId: String, hasPerson: Boolean, state: SurveillanceState) -> Unit)? = null

    fun start(config: CameraConfig) {
        this.config = config
        val provider = ProcessCameraProvider.getInstance(context).get()
        cameraProvider = provider
        provider.unbindAll()

        if (config.rearEnabled) {
            startRearCamera(provider)
        }
        if (config.frontEnabled) {
            startFrontCamera(provider)
        }
    }

    private fun startRearCamera(provider: ProcessCameraProvider) {
        try {
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            rearAnalysis = buildImageAnalysis("rear")
            rearVideoCapture = VideoCapture.withOutput(Recorder.Builder().build())

            rearCamera = provider.bindToLifecycle(
                lifecycleOwner, cameraSelector, rearAnalysis, rearVideoCapture
            )
            Log.i(TAG, "Rear camera started")
        } catch (e: Exception) {
            Log.e(TAG, "Rear camera failed: ${e.message}")
            onError("rear", e.message ?: "unknown")
        }
    }

    private fun startFrontCamera(provider: ProcessCameraProvider) {
        try {
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            frontAnalysis = buildImageAnalysis("front")
            frontVideoCapture = VideoCapture.withOutput(Recorder.Builder().build())

            frontCamera = provider.bindToLifecycle(
                lifecycleOwner, cameraSelector, frontAnalysis, frontVideoCapture
            )
            Log.i(TAG, "Front camera started")
        } catch (e: Exception) {
            Log.e(TAG, "Front camera failed: ${e.message}")
            onError("front", e.message ?: "unknown")
        }
    }

    private fun buildImageAnalysis(cameraId: String): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processFrame(imageProxy, cameraId)
                }
            }
    }

    private fun processFrame(imageProxy: ImageProxy, cameraId: String) {
        try {
            val motionDetector = if (cameraId == "rear") rearMotionDetector else frontMotionDetector
            val faceDetector = if (cameraId == "rear") rearFaceDetector else frontFaceDetector
            val stateMachine = if (cameraId == "rear") rearStateMachine else frontStateMachine

            val buffer = imageProxy.planes[0].buffer
            val width = imageProxy.width
            val height = imageProxy.height

            // 1. 帧差异运动检测
            val motionResult = motionDetector.analyze(buffer, width, height)

            // 2. 有人脸 → 直接判定有人
            if (motionResult.hasMotion) {
                faceDetector.detect(buffer, width, height, imageProxy.imageInfo.rotationDegrees) { faceResult ->
                    val hasPerson = faceResult.hasFace
                    stateMachine?.onFrameAnalysis(hasPerson, cameraId)
                    onFrameProcessed?.invoke(
                        cameraId, hasPerson,
                        stateMachine?.currentState ?: SurveillanceState.IDLE
                    )
                }
            } else {
                stateMachine?.onFrameAnalysis(false, cameraId)
                onFrameProcessed?.invoke(
                    cameraId, false,
                    stateMachine?.currentState ?: SurveillanceState.IDLE
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame processing error [$cameraId]: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    fun switchCamera(target: String) {
        when (target) {
            "front" -> {
                if (frontCamera == null) {
                    cameraProvider?.let { startFrontCamera(it) }
                }
            }
            "rear" -> {
                if (rearCamera == null) {
                    cameraProvider?.let { startRearCamera(it) }
                }
            }
        }
    }

    fun getStatus(): List<CameraStatus> = listOf(
        CameraStatus("rear", "back", rearCamera != null, rearStateMachine?.currentState?.name ?: "off"),
        CameraStatus("front", "front", frontCamera != null, frontStateMachine?.currentState?.name ?: "off")
    )

    fun stop() {
        cameraProvider?.unbindAll()
        rearMotionDetector.reset()
        frontMotionDetector.reset()
        rearFaceDetector.close()
        frontFaceDetector.close()
        cameraExecutor.shutdown()
        Log.i(TAG, "Camera manager stopped")
    }
}
