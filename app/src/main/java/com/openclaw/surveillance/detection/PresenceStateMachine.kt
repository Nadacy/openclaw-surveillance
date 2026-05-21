package com.openclaw.surveillance.detection

/**
 * 智能监控状态机
 *
 * IDLE ──(检测到人)──▶ DETECTING ──(确认有人)──▶ RECORDING
 *   ▲                                                  │
 *   └────────────(无人30秒)─────────────────────────────┘
 */
enum class SurveillanceState {
    /** 空闲，等待检测 */
    IDLE,
    /** 检测到动静，确认中（去抖） */
    DETECTING,
    /** 确认有人，正在录像 */
    RECORDING
}

class PresenceStateMachine(
    private val onStartRecording: () -> Unit,
    private val onStopRecording: () -> Unit,
    private val onStateChanged: (SurveillanceState, SurveillanceState) -> Unit
) {
    var currentState: SurveillanceState = SurveillanceState.IDLE
        private set

    /** 确认窗口：DETECTING 持续此时间后才进入 RECORDING */
    private val confirmWindowMs: Long = 1500L

    /** 停止阈值：RECORDING 状态超过此时间无人则停止 */
    private val stopThresholdMs: Long = 30_000L

    private var detectingStartMs: Long = 0L
    private var lastPersonSeenMs: Long = 0L
    private var personCount: Int = 0

    /** 收到帧分析结果：是否检测到人 */
    fun onFrameAnalysis(personDetected: Boolean, cameraId: String) {
        val now = System.currentTimeMillis()

        when (currentState) {
            SurveillanceState.IDLE -> {
                if (personDetected) {
                    transitionTo(SurveillanceState.DETECTING)
                    detectingStartMs = now
                }
            }

            SurveillanceState.DETECTING -> {
                if (personDetected) {
                    personCount++
                    // 去抖：确认窗口内至少 2 次检测到人
                    if (personCount >= 2 && (now - detectingStartMs) >= confirmWindowMs) {
                        transitionTo(SurveillanceState.RECORDING)
                        lastPersonSeenMs = now
                        onStartRecording()
                    }
                } else {
                    // 确认窗口内失去信号 → 回 IDLE
                    if (now - detectingStartMs > confirmWindowMs * 3) {
                        transitionTo(SurveillanceState.IDLE)
                        personCount = 0
                    }
                }
            }

            SurveillanceState.RECORDING -> {
                if (personDetected) {
                    lastPersonSeenMs = now
                } else {
                    // 超过阈值无人 → 停止录像
                    if (now - lastPersonSeenMs >= stopThresholdMs) {
                        onStopRecording()
                        transitionTo(SurveillanceState.IDLE)
                        personCount = 0
                    }
                }
            }
        }
    }

    private fun transitionTo(newState: SurveillanceState) {
        if (newState != currentState) {
            val old = currentState
            currentState = newState
            onStateChanged(old, newState)
        }
    }

    fun getStatus(): Map<String, Any> = mapOf(
        "state" to currentState.name,
        "personCount" to personCount,
        "lastPersonSeenMs" to lastPersonSeenMs,
        "confirmWindowMs" to confirmWindowMs,
        "stopThresholdMs" to stopThresholdMs
    )
}
