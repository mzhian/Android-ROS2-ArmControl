package com.ir.sdk.robotarm.ros

import com.ir.sdk.robotarm.model.Advertise
import com.ir.sdk.robotarm.model.Publish
import com.ir.sdk.robotarm.model.RosbridgeServiceResponse
import com.ir.sdk.robotarm.model.ServiceCall
import com.ir.sdk.robotarm.model.Subscribe
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake

class RosbridgeClient(
    private val rosbridgeUrl: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) {
    var loggingEnabled: Boolean = false

    enum class ConnectionState {
        IDLE, DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
    }

    private val responseAwaiters = ConcurrentHashMap<String, CompletableDeferred<RosbridgeServiceResponse>>()
    private val _incomingMessages = MutableSharedFlow<JsonObject>(extraBufferCapacity = 128)
    val incomingMessages: SharedFlow<JsonObject> = _incomingMessages

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _errorEvents = MutableSharedFlow<Throwable>(extraBufferCapacity = 16)
    val errorEvents: SharedFlow<Throwable> = _errorEvents

    private var socket: RosWebSocket? = null
    private var isExplicitlyClosed = false
    private var reconnectJob: Job? = null

    inner class RosWebSocket(uri: URI) : WebSocketClient(uri) {
        override fun onOpen(handshakedata: ServerHandshake?) {
            _connectionState.value = ConnectionState.CONNECTED
            isExplicitlyClosed = false
            reconnectJob?.cancel()
        }

        override fun onMessage(message: String?) {
            if (message.isNullOrBlank()) return
            if (loggingEnabled) Log.d("RosbridgeClient", "<< [RECV]: $message")
            scope.launch {
                val payload = runCatching {
                    json.parseToJsonElement(message).jsonObject
                }.getOrNull() ?: return@launch

                val id = payload["id"]?.toString()?.trim('"')
                if (!id.isNullOrBlank()) {
                    val response = runCatching {
                        json.decodeFromJsonElement<RosbridgeServiceResponse>(payload)
                    }.getOrNull()
                    if (response != null) {
                        val deferred = responseAwaiters.remove(id)
                        if (response.result == false) {
                            deferred?.completeExceptionally(Exception("ROS Service reported failure: $id"))
                        } else {
                            deferred?.complete(response)
                        }
                    }
                }
                _incomingMessages.emit(payload)
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            _connectionState.value = ConnectionState.DISCONNECTED
            failPendingCalls(IllegalStateException("Connection closed: $reason"))
            if (!isExplicitlyClosed) {
                attemptReconnect()
            }
        }

        override fun onError(ex: Exception?) {
            failPendingCalls(ex ?: IllegalStateException("Unknown error"))
        }
    }

    fun connect() {
        isExplicitlyClosed = false
        if (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING) return
        
        _connectionState.value = ConnectionState.CONNECTING
        socket?.close() // Ensure old socket is closed
        socket = RosWebSocket(URI(rosbridgeUrl))
        socket?.connect()
    }

    fun disconnect() {
        isExplicitlyClosed = true
        reconnectJob?.cancel()
        socket?.close()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun attemptReconnect() {
        if (reconnectJob?.isActive == true) return
        _connectionState.value = ConnectionState.RECONNECTING
        reconnectJob = scope.launch {
            var delayMs = 1000L
            while (!isExplicitlyClosed && _connectionState.value != ConnectionState.CONNECTED) {
                delay(delayMs)
                connect()
                delayMs = (delayMs * 2).coerceAtMost(30000L) // 指数退避，最高 30秒
            }
        }
    }

    private fun failPendingCalls(cause: Throwable) {
        val iterator = responseAwaiters.values.iterator()
        while (iterator.hasNext()) {
            val deferred = iterator.next()
            if (!deferred.isCompleted) {
                deferred.completeExceptionally(cause)
            }
            iterator.remove()
        }
    }

    fun publish(topic: String, msg: JsonElement, type: String): Boolean {
        return send(Publish(topic = topic, msg = msg, type = type))
    }

    fun subscribe(topic: String, messageType: String? = null): Boolean {
        return send(Subscribe(topic = topic, type = messageType))
    }

    fun advertise(topic: String, type: String): Boolean {
        return send(Advertise(topic = topic, type = type))
    }

    fun unadvertise(topic: String): Boolean {
        return send(Unadvertise(topic = topic))
    }

    fun unsubscribe(topic: String): Boolean {
        return send(Unsubscribe(topic = topic))
    }

    suspend fun callService(service: String, args: JsonElement = buildJsonObject { }): RosbridgeServiceResponse {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<RosbridgeServiceResponse>()
        responseAwaiters[requestId] = deferred

        try {
            send(ServiceCall(service = service, args = args, id = requestId))
            return withTimeout(10000) { // 10秒超时
                deferred.await()
            }
        } finally {
            // 确保无论成功还是由于超时/取消失败，都从等待队列中移除，防止内存泄漏
            responseAwaiters.remove(requestId)
        }
    }

    private inline fun <reified T : Any> send(message: T): Boolean {
        val jsonStr = json.encodeToString(message)
        if (loggingEnabled) Log.d("RosbridgeClient", ">> [SEND]: $jsonStr")
        
        return if (_connectionState.value == ConnectionState.CONNECTED) {
            try {
                socket?.send(jsonStr)
                true
            } catch (e: Exception) {
                Log.e("RosbridgeClient", "Failed to send message: ${e.message}")
                scope.launch { _errorEvents.emit(e) }
                false
            }
        } else {
            val error = IllegalStateException("WebSocket not connected (State: ${_connectionState.value})")
            Log.w("RosbridgeClient", error.message ?: "Not connected")
            scope.launch { _errorEvents.emit(error) }
            false
        }
    }
}
