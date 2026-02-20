package com.example.test

data class MeshEdge(
    val from: Int,
    val to: Int,
    val timestamp: Long = System.currentTimeMillis()
)
