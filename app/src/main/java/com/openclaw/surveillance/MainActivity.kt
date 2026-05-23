package com.openclaw.surveillance

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.openclaw.surveillance.service.SurveillanceService
import com.openclaw.surveillance.camera.CameraManager

/**
 * 简易启动/状态页面
 *
 * 安装后桌面会有图标，点击可查看监控状态、启动/停止服务。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var dimButton: Button
    private lateinit var snapButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // 标题
        layout.addView(TextView(this).apply {
            text = "🔍 OpenClaw 智能监控"
            textSize = 22f
            setPadding(0, 0, 0, 16)
        })

        // 状态
        statusText = TextView(this).apply {
            text = "状态: 未启动"
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(statusText)

        // 启动按钮
        startButton = Button(this).apply {
            text = "▶ 启动监控"
            setOnClickListener {
                val intent = Intent(this@MainActivity, SurveillanceService::class.java)
                intent.action = "com.openclaw.surveillance.START"
                intent.putExtra("rear", true)
                intent.putExtra("front", false)
                startForegroundService(intent)
                statusText.text = "状态: 监控运行中"
            }
        }
        layout.addView(startButton)

        // 停止按钮
        stopButton = Button(this).apply {
            text = "⏹ 停止监控"
            setOnClickListener {
                val intent = Intent(this@MainActivity, SurveillanceService::class.java)
                intent.action = "com.openclaw.surveillance.STOP"
                startService(intent)
                stopService(Intent(this@MainActivity, SurveillanceService::class.java))
                statusText.text = "状态: 已停止"
            }
        }
        layout.addView(stopButton)

        // 假关屏按钮
        dimButton = Button(this).apply {
            text = "🌑 假关屏"
            setOnClickListener {
                val intent = Intent(this@MainActivity, SurveillanceService::class.java)
                intent.action = "com.openclaw.surveillance.DIM"
                startService(intent)
                statusText.text = "状态: 假关屏中 (监控持续运行)"
            }
        }
        layout.addView(dimButton)

        // 拍照按钮
        snapButton = Button(this).apply {
            text = "📸 拍照"
            setOnClickListener {
                val intent = Intent(this@MainActivity, SurveillanceService::class.java)
                intent.action = "com.openclaw.surveillance.SNAP"
                intent.putExtra("camera", "rear")
                startService(intent)
            }
        }
        layout.addView(snapButton)

        // 提示
        layout.addView(TextView(this).apply {
            text = "\n💡 提示: 安装后由 OpenClaw Gateway 远程控制\n可通过飞书发送命令操作监控模块"
            textSize = 13f
            setPadding(0, 24, 0, 0)
        })

        scroll.addView(layout)
        setContentView(scroll)
    }
}
