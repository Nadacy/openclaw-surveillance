package com.openclaw.surveillance.detection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.nio.ByteBuffer

/**
 * 帧差异运动检测器
 *
 * 比较连续帧的像素变化率来判断是否有物体运动。
 * 纯本地计算，不消耗网络流量/API 费用。
 */
class MotionDetector(
    /** 运动阈值：变化像素占比超过此值认为有运动 */
    private val motionThreshold: Float = 0.05f,
    /** 采样步长：每隔 N 个像素采样一次（性能优化） */
    private val sampleStep: Int = 4
) {
    companion object {
        private const val TAG = "MotionDetector"
    }

    private var previousFrame: ByteArray? = null
    private var frameWidth: Int = 0
    private var frameHeight: Int = 0

    /**
     * 分析一帧，返回是否有运动
     */
    fun analyze(frameData: ByteBuffer, width: Int, height: Int): MotionResult {
        val currentBytes = ByteArray(frameData.remaining())
        frameData.get(currentBytes)
        frameData.rewind()

        this.frameWidth = width
        this.frameHeight = height

        val prev = previousFrame
        previousFrame = currentBytes

        if (prev == null || prev.size != currentBytes.size) {
            return MotionResult(false, 0f, "init")
        }

        val totalPixels = currentBytes.size
        var changedPixels = 0
        var sampled = 0

        var i = 0
        while (i < totalPixels) {
            if (kotlin.math.abs(currentBytes[i].toInt() - prev[i].toInt()) > 30) {
                changedPixels++
            }
            sampled++
            i += sampleStep
        }

        val changeRatio = if (sampled > 0) changedPixels.toFloat() / sampled else 0f
        val hasMotion = changeRatio >= motionThreshold

        return MotionResult(
            hasMotion = hasMotion,
            changeRatio = changeRatio,
            detail = if (hasMotion) "motion_detected" else "still"
        )
    }

    fun reset() {
        previousFrame = null
    }

    data class MotionResult(
        val hasMotion: Boolean,
        val changeRatio: Float,
        val detail: String
    )
}
