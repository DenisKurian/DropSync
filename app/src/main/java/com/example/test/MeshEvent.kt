package com.example.test

data class MeshEvent(
    val packetId: Int,
    val srcNodeId: Int,
    val message: String,
    val ttl: Int,
    val timestamp: Long = System.currentTimeMillis()
)
