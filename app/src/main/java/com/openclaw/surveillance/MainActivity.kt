package com.openclaw.surveillance

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.openclaw.surveillance.protocol.GatewayClient
import com.openclaw.surveillance.service.SurveillanceService

/**
 * 主界面：连接服务器 + 监控控制
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var gatewayClient: GatewayClient

    private lateinit var urlInput: EditText
    private lateinit var connectBtn: Button
    private lateinit var disconnectBtn: Button
    private lateinit var statusText: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var snapBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("surveillance", MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", "http://139.159.176.173:18789") ?: "http://139.159.176.173:18789"

        gatewayClient = GatewayClient(
            serverUrl = savedUrl,
            onStatusChange = { status -> runOnUiThread { statusText.text = "状态: $status" } },
            onCommand = { command, params -> handleCommand(command, params) }
        )

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        // 标题
        layout.addView(TextView(this).apply {
            text = "🔍 智能监控"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        })

        // ── 服务器连接 ──
        layout.addView(TextView(this).apply {
            text = "服务器地址"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })

        urlInput = EditText(this).apply {
            setText(savedUrl)
            hint = "http://IP:18789"
            setSingleLine()
        }
        layout.addView(urlInput)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }

        connectBtn = Button(this).apply {
            text = "🔗 连接"
            setOnClickListener {
                val url = urlInput.text.toString().trim()
                prefs.edit().putString("server_url", url).apply()
                gatewayClient.updateUrl(url)
                gatewayClient.connect()
            }
        }
        btnRow.addView(connectBtn)

        disconnectBtn = Button(this).apply {
            text = "断开"
            setOnClickListener { gatewayClient.disconnect() }
        }
        btnRow.addView(disconnectBtn)
        layout.addView(btnRow)

        // 状态
        statusText = TextView(this).apply {
            text = "状态: 未连接"
            textSize = 16f
            setPadding(0, 16, 0, 24)
        }
        layout.addView(statusText)

        // ── 分隔线 ──
        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2
            ).apply { setMargins(0, 0, 0, 24) }
            setBackgroundColor(0x33000000.toInt())
        })

        // ── 监控控制 ──
        layout.addView(TextView(this).apply {
            text = "监控控制"
            textSize = 18f
            setPadding(0, 0, 0, 12)
        })

        startBtn = Button(this).apply {
            text = "▶ 启动监控"
            setOnClickListener {
                val intent = Intent(this@MainActivity, SurveillanceService::class.java)
                intent.action = "com.openclaw.surveillance.START"
                intent.putExtra("rear", true)
                intent.putExtra("front", false)
                startForegroundService(intent)
                gatewayClient.sendResult("surveillance.start", mapOf("status" to "started"))
            }
        }
        layout.addView(startBtn)

        stopBtn = Button(this).apply {
            text = "⏹ 停止监控"
            setOnClickListener {
                stopService(Intent(this@MainActivity, SurveillanceService::class.java))
                gatewayClient.sendResult("surveillance.stop", mapOf("status" to "stopped"))
            }
        }
        layout.addView(stopBtn)

        snapBtn = Button(this).apply {
            text = "📸 拍照"
            setOnClickListener {
                val intent = Intent(this@MainActivity, SurveillanceService::class.java)
                intent.action = "com.openclaw.surveillance.SNAP"
                intent.putExtra("camera", "rear")
                startService(intent)
            }
        }
        layout.addView(snapBtn)

        // 提示
        layout.addView(TextView(this).apply {
            text = "\n💡 输入服务器 IP + 端口，点击连接\n连接成功后 Gateway 可远程发送命令"
            textSize = 13f
            setPadding(0, 24, 0, 0)
        })

        scroll.addView(layout)
        setContentView(scroll)
    }

    private fun handleCommand(command: String, params: Map<String, Any>) {
        runOnUiThread {
            when (command) {
                "camera.snap" -> {
                    val intent = Intent(this, SurveillanceService::class.java)
                    intent.action = "com.openclaw.surveillance.SNAP"
                    intent.putExtra("camera", params["camera"] as? String ?: "rear")
                    startService(intent)
                }
                "surveillance.start" -> {
                    val intent = Intent(this, SurveillanceService::class.java)
                    intent.action = "com.openclaw.surveillance.START"
                    startForegroundService(intent)
                }
                "surveillance.stop" -> {
                    stopService(Intent(this, SurveillanceService::class.java))
                }
            }
        }
    }

    override fun onDestroy() {
        gatewayClient.destroy()
        super.onDestroy()
    }
}
