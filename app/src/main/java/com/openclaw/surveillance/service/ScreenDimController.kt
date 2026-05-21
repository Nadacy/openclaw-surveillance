package com.openclaw.surveillance.service

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager

/**
 * 假关屏控制器
 *
 * 将屏幕亮度降到最低 + 显示黑色覆盖层，
 * 外表看起来像关屏，实际相机仍在运行。
 */
class ScreenDimController(private val context: Context) {
    companion object {
        private const val TAG = "ScreenDimCtrl"
        private const val DIM_BRIGHTNESS = 0  // 0 = 最低亮度
    }

    private var originalBrightness: Int = 128
    private var isDimmed = false
    private var wakeLock: PowerManager.WakeLock? = null

    /** 假关屏 */
    fun dim() {
        if (isDimmed) return

        try {
            // 1. 记录原始亮度
            originalBrightness = try {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                )
            } catch (e: Exception) { 128 }

            // 2. 调亮度到 0
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    DIM_BRIGHTNESS
                )
            }

            // 3. CPU 保持唤醒
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "openclaw:surveillance"
            ).apply {
                acquire(12 * 60 * 60 * 1000L)  // 最长 12 小时
            }

            isDimmed = true
            Log.i(TAG, "Screen dimmed (fake off), WakeLock ON")
        } catch (e: Exception) {
            Log.e(TAG, "Dim failed: ${e.message}")
        }
    }

    /** 恢复亮度 */
    fun undim() {
        if (!isDimmed) return

        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    originalBrightness
                )
            }

            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null

            isDimmed = false
            Log.i(TAG, "Screen restored, WakeLock OFF")
        } catch (e: Exception) {
            Log.e(TAG, "Undim failed: ${e.message}")
        }
    }

    val isActive: Boolean get() = isDimmed
}
