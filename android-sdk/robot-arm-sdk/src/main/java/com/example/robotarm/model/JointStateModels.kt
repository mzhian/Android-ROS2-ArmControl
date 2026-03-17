package com.example.robotarm.model

data class JointState(
    val names: List<String>,
    val positions: List<Double>,
    val velocities: List<Double>,
    val efforts: List<Double>,
)
