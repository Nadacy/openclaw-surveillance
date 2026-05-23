package com.openclaw.surveillance.protocol

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket 客户端，连接 OpenClaw Gateway
 *
 * 连接到 Gateway 后注册为节点，接收命令并上报结果。
 */
class GatewayClient(
    private var serverUrl: String,
    private val onStatusChange: (String) -> Unit,
    private val onCommand: (String, Map<String, Any>) -> Unit
) {
    companion object {
        private const val TAG = "GatewayClient"
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_DELAY_MS = 60000L
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket 不超时
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private var shouldReconnect = true

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        shouldReconnect = true
        reconnectAttempts = 0
        doConnect()
    }

    private fun doConnect() {
        val url = serverUrl.trimEnd('/') + "/ws"
        Log.i(TAG, "Connecting to $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                reconnectAttempts = 0
                Log.i(TAG, "Connected to Gateway")
                onStatusChange("已连接 ✅")
                // 注册为监控节点
                registerAsNode()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type", "")
                    val command = json.optString("command", "")
                    val params = mutableMapOf<String, Any>()
                    json.optJSONObject("params")?.let { p ->
                        p.keys().forEach { key ->
                            params[key] = p.get(key)
                        }
                    }
                    if (command.isNotEmpty()) {
                        onCommand(command, params)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.i(TAG, "Closed: $code $reason")
                onStatusChange("已断开")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e(TAG, "Connection failed: ${t.message}")
                onStatusChange("连接失败 ❌: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    private fun registerAsNode() {
        val msg = JSONObject().apply {
            put("type", "register")
            put("nodeId", "matepad-surveillance")
            put("capabilities", JSONObject().apply {
                put("camera", true)
                put("surveillance", true)
                put("snap", true)
                put("clip", true)
            })
        }
        send(msg.toString())
    }

    fun send(jsonMessage: String) {
        webSocket?.send(jsonMessage)
    }

    fun sendResult(command: String, result: Map<String, Any>) {
        val msg = JSONObject().apply {
            put("type", "result")
            put("command", command)
            put("data", JSONObject().apply {
                result.forEach { (k, v) -> put(k, v) }
            })
            put("timestamp", System.currentTimeMillis())
        }
        send(msg.toString())
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        isConnected = false
        onStatusChange("未连接")
    }

    fun updateUrl(newUrl: String) {
        serverUrl = newUrl
    }

    fun isConnected(): Boolean = isConnected

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        val delay = (RECONNECT_DELAY_MS * (reconnectAttempts + 1)).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectAttempts++
        Log.i(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts)")
        onStatusChange("${delay / 1000}s 后重连...")

        scope.launch {
            delay(delay)
            if (shouldReconnect) {
                doConnect()
            }
        }
    }

    fun destroy() {
        shouldReconnect = false
        scope.cancel()
        webSocket?.close(1000, null)
    }
}
