package com.example.test

import java.nio.ByteBuffer

data class MeshPacket(
    val version: Byte,
    val type: Byte,
    val srcNodeId: Int,
    val destNodeId: Int,
    val ttl: Byte,
    val payload: ByteArray = byteArrayOf()
) {

    fun toBytes(): ByteArray {

        val buffer = ByteBuffer.allocate(
            BLEConstants.HEADER_SIZE + payload.size
        )

        buffer.put(version)
        buffer.put(type)
        buffer.putInt(srcNodeId)
        buffer.putInt(destNodeId)
        buffer.put(ttl)
        buffer.put(payload.size.toByte())
        buffer.put(payload)

        return buffer.array()
    }

    companion object {

        fun fromBytes(data: ByteArray): MeshPacket? {

            if (data.size < BLEConstants.HEADER_SIZE) return null

            val buffer = ByteBuffer.wrap(data)

            val version = buffer.get()
            val type = buffer.get()
            val src = buffer.int
            val dest = buffer.int
            val ttl = buffer.get()
            val payloadLen = buffer.get().toInt()

            if (payloadLen < 0) return null
            if (payloadLen > BLEConstants.MAX_PAYLOAD_SIZE) return null
            if (data.size < BLEConstants.HEADER_SIZE + payloadLen) return null

            val payload = ByteArray(payloadLen)
            buffer.get(payload)

            return MeshPacket(
                version = version,
                type = type,
                srcNodeId = src,
                destNodeId = dest,
                ttl = ttl,
                payload = payload
            )
        }
    }
}
