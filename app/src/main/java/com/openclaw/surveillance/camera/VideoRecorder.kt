package com.openclaw.surveillance.camera

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.camera.video.*
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

/**
 * 录像+录音模块
 *
 * 通过 CameraX VideoCapture + MediaRecorder 录像，输出 MP4 → Base64。
 */
class VideoRecorder {
    companion object {
        private const val TAG = "VideoRecorder"
        private const val MAX_BASE64_SIZE = 5 * 1024 * 1024  // 5MB per chunk
    }

    data class RecordResult(
        val success: Boolean,
        val base64: String? = null,
        val filePath: String? = null,
        val durationMs: Long = 0,
        val hasAudio: Boolean = true,
        val error: String? = null
    )

    private var activeRecording: Recording? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L
    private val executor = Executors.newSingleThreadExecutor()

    fun startRecording(
        videoCapture: VideoCapture<Recorder>,
        outputDir: File,
        includeAudio: Boolean = true,
        onResult: (RecordResult) -> Unit
    ) {
        outputFile = File(outputDir, "surveillance_${System.currentTimeMillis()}.mp4")
        startTimeMs = System.currentTimeMillis()

        val mediaStoreOutput = FileOutputOptions.Builder(outputFile!!).build()

        val recording = videoCapture.output
            .prepareRecording(context = null, mediaStoreOutput)  // context not needed for file output
            .apply {
                if (includeAudio) {
                    withAudioEnabled()
                }
            }
            .start(executor) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Finalize -> {
                        val durationMs = System.currentTimeMillis() - startTimeMs
                        if (recordEvent.hasError()) {
                            Log.e(TAG, "Video recording error: ${recordEvent.error}")
                            onResult(RecordResult(
                                success = false,
                                error = "Recording error: ${recordEvent.error}"
                            ))
                        } else {
                            val base64 = fileToBase64(outputFile!!)
                            onResult(RecordResult(
                                success = true,
                                base64 = base64,
                                filePath = outputFile?.absolutePath,
                                durationMs = durationMs,
                                hasAudio = includeAudio
                            ))
                        }
                    }
                    is VideoRecordEvent.Status -> {
                        Log.d(TAG, "Recording status: ${recordEvent.recordingStats}")
                    }
                    else -> {}
                }
            }

        activeRecording = recording
        Log.i(TAG, "Recording started: ${outputFile?.absolutePath}")
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
        Log.i(TAG, "Recording stopped")
    }

    fun isRecording(): Boolean = activeRecording != null

    private fun fileToBase64(file: File): String? {
        return try {
            val bytes = FileInputStream(file).use { it.readBytes() }
            if (bytes.size <= MAX_BASE64_SIZE) {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                // 视频太大，只传路径，不传 Base64
                Log.w(TAG, "Video too large for base64: ${bytes.size} bytes, path only")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "File read error: ${e.message}")
            null
        }
    }
}
