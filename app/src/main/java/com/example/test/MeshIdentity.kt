package com.example.test

import android.content.Context
import java.util.UUID

object MeshIdentity {

    private const val PREF_NAME = "mesh_identity"
    private const val KEY_NODE_ID = "node_id"

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

        val newId = UUID.randomUUID().hashCode()

        prefs.edit()
            .putInt(KEY_NODE_ID, newId)
            .apply()

        return newId
    }
}
