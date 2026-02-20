package com.example.test

import android.content.Context
import android.util.Log
import java.util.UUID

object MeshIdentity {

    private const val PREF_NAME = "mesh_identity"
    private const val KEY_NODE_ID = "node_id"
    private const val TAG = "MESH"

    /**
     * Persistent 32-bit node ID.
     * Generated once, reused forever.
     */
    fun getNodeId(context: Context): Int {

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val existing = prefs.getInt(KEY_NODE_ID, 0)
        if (existing != 0) {
            return existing
        }

        var newId: Int

        // Ensure we never collide with broadcast ID (-1)
        do {
            newId = UUID.randomUUID().hashCode()
        } while (newId == 0 || newId == BLEConstants.BROADCAST_NODE_ID)

        prefs.edit()
            .putInt(KEY_NODE_ID, newId)
            .apply()

        Log.d(TAG, "My Node ID = ${newId.toString(16)}")

        return newId
    }
}
