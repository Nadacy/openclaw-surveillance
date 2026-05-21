package com.openclaw.surveillance.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService

/**
 * 拍照模块
 *
 * 通过 CameraX ImageCapture 拍照，输出 JPEG → Base64。
 */
class PhotoCapture(
    private val imageCapture: ImageCapture,
    private val executor: ExecutorService
) {
    companion object {
        private const val TAG = "PhotoCapture"
    }

    data class PhotoResult(
        val success: Boolean,
        val base64: String? = null,
        val filePath: String? = null,
        val width: Int = 0,
        val height: Int = 0,
        val error: String? = null
    )

    fun capture(outputDir: File, onResult: (PhotoResult) -> Unit) {
        val photoFile = File(outputDir, "snap_${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                        val base64 = bitmapToBase64(bitmap)
                        onResult(PhotoResult(
                            success = true,
                            base64 = base64,
                            filePath = photoFile.absolutePath,
                            width = bitmap.width,
                            height = bitmap.height
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Base64 encoding failed: ${e.message}")
                        onResult(PhotoResult(success = false, error = e.message))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}")
                    onResult(PhotoResult(
                        success = false,
                        error = "Capture failed: ${exception.message}"
                    ))
                }
            }
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 85): String {
        val maxSize = 4 * 1024 * 1024  // 4MB cap for base64
        var q = quality
        var output = ByteArrayOutputStream()

        do {
            output.reset()
            if (bitmap.width > 1600) {
                val ratio = 1600f / bitmap.width
                val scaled = Bitmap.createScaledBitmap(
                    bitmap, 1600, (bitmap.height * ratio).toInt(), true
                )
                scaled.compress(Bitmap.CompressFormat.JPEG, q, output)
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, q, output)
            }
            q -= 10
        } while (output.size() > maxSize && q > 20)

        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
}
