package com.ir.sdk.robotarm.model

import kotlinx.serialization.Serializable

@Serializable
data class JointState(
    val name: List<String> = emptyList(),
    val position: List<Double> = emptyList(),
    val velocity: List<Double> = emptyList(),
    val effort: List<Double> = emptyList(),
)
