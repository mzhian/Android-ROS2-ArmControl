package com.example.robotarm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed interface RosbridgeMessage {
    val op: String
}

@Serializable
data class Advertise(
    override val op: String = "advertise",
    val topic: String,
    val type: String,
) : RosbridgeMessage

@Serializable
data class Publish(
    override val op: String = "publish",
    val topic: String,
    val msg: JsonElement,
) : RosbridgeMessage

@Serializable
data class Subscribe(
    override val op: String = "subscribe",
    val topic: String,
    val type: String? = null,
    @SerialName("throttle_rate") val throttleRate: Int? = null,
    @SerialName("queue_length") val queueLength: Int? = null,
) : RosbridgeMessage

@Serializable
data class ServiceCall(
    override val op: String = "call_service",
    val service: String,
    val args: JsonElement? = null,
    val id: String? = null,
) : RosbridgeMessage

@Serializable
data class RosbridgeServiceResponse(
    val op: String,
    val service: String? = null,
    val values: JsonElement? = null,
    val id: String? = null,
    val result: Boolean? = null,
)
