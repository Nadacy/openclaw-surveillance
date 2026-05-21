package com.openclaw.surveillance.detection

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.nio.ByteBuffer

/**
 * ML Kit 离线人脸检测器
 *
 * 使用 Google ML Kit 离线模型，不依赖 Google Play Services。
 * 完全本地运行，不消耗网络流量/API 费用。
 */
class FaceDetector {
    companion object {
        private const val TAG = "FaceDetector"
    }

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)  // 快速模式
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)         // 不画轮廓，更快
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.15f)  // 最小人脸占画面 15%
        .build()

    private val detector = FaceDetection.getClient(options)

    /**
     * 检测帧中是否有人脸
     */
    fun detect(
        frameData: ByteBuffer,
        width: Int,
        height: Int,
        rotation: Int,
        onResult: (FaceResult) -> Unit
    ) {
        val image = InputImage.fromByteBuffer(
            frameData,
            width,
            height,
            rotation,
            InputImage.IMAGE_FORMAT_NV21
        )

        detector.process(image)
            .addOnSuccessListener { faces: List<Face> ->
                onResult(FaceResult(
                    faceCount = faces.size,
                    hasFace = faces.isNotEmpty(),
                    faces = faces.map { face ->
                        val box = face.boundingBox
                        FaceInfo(
                            bounds = RectInfo(
                                left = box.left,
                                top = box.top,
                                right = box.right,
                                bottom = box.bottom
                            ),
                            trackingId = face.trackingId ?: -1,
                            smilingProb = face.smilingProbability ?: 0f
                        )
                    }
                ))
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Face detection failed: ${e.message}")
                onResult(FaceResult(faceCount = 0, hasFace = false, faces = emptyList()))
            }
    }

    fun close() {
        detector.close()
    }

    data class FaceResult(
        val faceCount: Int,
        val hasFace: Boolean,
        val faces: List<FaceInfo>
    )

    data class FaceInfo(
        val bounds: RectInfo,
        val trackingId: Int,
        val smilingProb: Float
    )

    data class RectInfo(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
}
