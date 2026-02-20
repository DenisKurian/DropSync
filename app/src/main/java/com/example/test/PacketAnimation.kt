package com.example.test

data class PacketAnimation(
    val from: Int,
    val to: Int,
    var progress: Float = 0f   // ← THIS WAS MISSING
)
