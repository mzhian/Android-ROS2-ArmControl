package com.example.robotarm.ros

import com.example.robotarm.model.Advertise
import com.example.robotarm.model.Publish
import com.example.robotarm.model.RosbridgeServiceResponse
import com.example.robotarm.model.ServiceCall
import com.example.robotarm.model.Subscribe
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake

class RosbridgeClient(
    rosbridgeUrl: String,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    private val responseAwaiters = ConcurrentHashMap<String, CompletableDeferred<RosbridgeServiceResponse>>()
    private val _incomingMessages = MutableSharedFlow<JsonObject>(extraBufferCapacity = 128)

    val incomingMessages: SharedFlow<JsonObject> = _incomingMessages

    private val socket = object : WebSocketClient(URI(rosbridgeUrl)) {
        override fun onOpen(handshakedata: ServerHandshake?) = Unit

        override fun onMessage(message: String?) {
            if (message.isNullOrBlank()) return

            val payload = runCatching {
                json.parseToJsonElement(message).jsonObject
            }.getOrNull() ?: return

            val id = payload["id"]?.toString()?.trim('"')
            if (!id.isNullOrBlank()) {
                val response = runCatching {
                    json.decodeFromJsonElement<RosbridgeServiceResponse>(payload)
                }.getOrNull()
                if (response != null) {
                    responseAwaiters.remove(id)?.complete(response)
                }
            }

            _incomingMessages.tryEmit(payload)
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) = Unit

        override fun onError(ex: Exception?) {
            responseAwaiters.values.forEach { deferred ->
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(ex ?: IllegalStateException("Unknown websocket error"))
                }
            }
            responseAwaiters.clear()
        }
    }

    fun connect() {
        socket.connect()
    }

    fun disconnect() {
        socket.close()
    }

    fun advertise(topic: String, messageType: String) {
        send(Advertise(topic = topic, type = messageType))
    }

    fun publish(topic: String, message: JsonElement) {
        send(Publish(topic = topic, msg = message))
    }

    fun subscribe(topic: String, messageType: String? = null, throttleRate: Int? = null, queueLength: Int? = null) {
        send(
            Subscribe(
                topic = topic,
                type = messageType,
                throttleRate = throttleRate,
                queueLength = queueLength,
            ),
        )
    }

    suspend fun callService(service: String, args: JsonObject = buildJsonObject { }): RosbridgeServiceResponse {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<RosbridgeServiceResponse>()
        responseAwaiters[requestId] = deferred

        send(ServiceCall(service = service, args = args, id = requestId))
        return deferred.await()
    }

    private fun send(payload: Any) {
        val message = when (payload) {
            is Advertise -> json.encodeToString(payload)
            is Publish -> json.encodeToString(payload)
            is Subscribe -> json.encodeToString(payload)
            is ServiceCall -> json.encodeToString(payload)
            else -> json.encodeToString(buildJsonObject {
                put("op", "unknown")
            })
        }
        socket.send(message)
    }
}
