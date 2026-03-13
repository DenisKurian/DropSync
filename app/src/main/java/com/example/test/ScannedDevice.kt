package com.example.test

data class ScannedDevice(
    val nodeId: Int,
    val address: String,
    val name: String?,
    val rssi: Int
)
