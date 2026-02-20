package com.example.test

data class MeshNode(
    val nodeId: Int,
    val lastSeen: Long = System.currentTimeMillis()
)
