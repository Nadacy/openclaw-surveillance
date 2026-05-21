package com.openclaw.surveillance.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openclaw.surveillance.camera.CameraManager
import com.openclaw.surveillance.detection.PresenceStateMachine
import com.openclaw.surveillance.protocol.CommandHandler

/**
 * 智能监控前台服务
 *
 * 通知栏显示 "📹 监控运行中"，保持进程不被系统杀死。
 * 集成摄像头、检测、录像、假关屏。
 */
class SurveillanceService : androidx.lifecycle.LifecycleService() {
    companion object {
        private const val TAG = "SurveillanceService"
        private const val NOTIFICATION_ID = 4279
        const val CHANNEL_ID = "surveillance_channel"
        const val ACTION_STOP = "com.openclaw.surveillance.STOP"
    }

    inner class LocalBinder : Binder() {
        fun getService(): SurveillanceService = this@SurveillanceService
    }

    // 核心组件
    lateinit var cameraManager: CameraManager
    lateinit var screenDimController: ScreenDimController
    lateinit var commandHandler: CommandHandler

    // LifecycleService provides onBind; use it directly

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SurveillanceService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("正在初始化..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSurveillance()
            "START" -> startSurveillance(
                rearEnabled = intent.getBooleanExtra("rear", true),
                frontEnabled = intent.getBooleanExtra("front", false)
            )
            "DIM" -> screenDimController.dim()
            "UNDIM" -> screenDimController.undim()
        }
        return START_STICKY  // 被杀后自动重启
    }

    fun startSurveillance(rearEnabled: Boolean, frontEnabled: Boolean) {
        // 初始化控制器
        screenDimController = ScreenDimController(this)
        commandHandler = CommandHandler(this)

        // 初始化摄像头
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this as? androidx.lifecycle.LifecycleOwner
                ?: throw IllegalStateException("Service must be LifecycleOwner"),
            onError = { camId, error ->
                commandHandler.sendGatewayMessage("error.camera", mapOf(
                    "camera" to camId,
                    "error" to error
                ))
            }
        )

        // 初始化后置状态机
        cameraManager.rearStateMachine = PresenceStateMachine(
            onStartRecording = {
                cameraManager.rearVideoCapture?.let { vc ->
                    commandHandler.onSurveillanceStart("rear")
                }
            },
            onStopRecording = {
                commandHandler.onSurveillanceStop("rear")
            },
            onStateChanged = { old, new ->
                commandHandler.sendGatewayMessage("surveillance.state", mapOf(
                    "camera" to "rear",
                    "from" to old.name,
                    "to" to new.name
                ))
            }
        )

        // 初始化前置状态机
        cameraManager.frontStateMachine = PresenceStateMachine(
            onStartRecording = {
                cameraManager.frontVideoCapture?.let { vc ->
                    commandHandler.onSurveillanceStart("front")
                }
            },
            onStopRecording = {
                commandHandler.onSurveillanceStop("front")
            },
            onStateChanged = { old, new ->
                commandHandler.sendGatewayMessage("surveillance.state", mapOf(
                    "camera" to "front",
                    "from" to old.name,
                    "to" to new.name
                ))
            }
        )

        // 启动摄像头
        cameraManager.start(CameraManager.CameraConfig(rearEnabled, frontEnabled))

        // 更新通知栏
        updateNotification("监控运行中")
        Log.i(TAG, "Surveillance started: rear=$rearEnabled, front=$frontEnabled")
    }

    fun stopSurveillance() {
        cameraManager.stop()
        screenDimController.undim()
        updateNotification("已停止")
        stopSelf()
        Log.i(TAG, "Surveillance stopped")
    }

    fun toggleDim(): Boolean {
        return if (screenDimController.isActive) {
            screenDimController.undim()
            false
        } else {
            screenDimController.dim()
            true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "智能监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "OpenClaw 智能监控服务"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📹 智能监控")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                "停止",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, SurveillanceService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }
}
