package com.example.test

import java.util.concurrent.ConcurrentHashMap

class NeighborTable(
    private val expiryMillis: Long = 30_000L // 30 seconds
) {

    private val neighbors = ConcurrentHashMap<Int, Neighbor>()

    /** Add or update a neighbor */
    fun update(nodeId: Int, address: String, rssi: Int) {
        neighbors[nodeId] = Neighbor(
            nodeId = nodeId,
            address = address,
            rssi = rssi,
            lastSeen = System.currentTimeMillis()
        )
    }

    /** Remove expired neighbors */
    fun prune() {
        val now = System.currentTimeMillis()
        neighbors.entries.removeIf {
            now - it.value.lastSeen > expiryMillis
        }
    }

    /** Get active neighbors */
    fun getAll(): List<Neighbor> {
        prune()
        return neighbors.values.sortedByDescending { it.rssi }
    }

    /** Check if node is a neighbor */
    fun contains(nodeId: Int): Boolean {
        return neighbors.containsKey(nodeId)
    }

    /** Clear table */
    fun clear() {
        neighbors.clear()
    }
}

