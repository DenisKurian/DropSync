package com.example.test

import java.util.concurrent.ConcurrentHashMap

class NeighborTable {

    private val neighbors =
        ConcurrentHashMap<Int, Neighbor>()

    private val STALE_TIMEOUT = 15000L

    fun update(
        nodeId: Int,
        address: String,
        rssi: Int,
        name: String
    ) {

        neighbors[nodeId] = Neighbor(
            nodeId = nodeId,
            address = address,
            rssi = rssi,
            name = name,
            lastSeen = System.currentTimeMillis()
        )
    }

    fun getAll(): List<Neighbor> {

        val now = System.currentTimeMillis()

        neighbors.entries.removeIf {
            now - it.value.lastSeen > STALE_TIMEOUT
        }

        return neighbors.values.toList()
    }

    fun get(nodeId: Int): Neighbor? {
        return neighbors[nodeId]
    }
}