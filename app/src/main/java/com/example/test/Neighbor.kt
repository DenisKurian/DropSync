package com.example.test

data class Neighbor(
    val nodeId: Int,
    val address: String,
    val rssi: Int,
    val lastSeen: Long
)
