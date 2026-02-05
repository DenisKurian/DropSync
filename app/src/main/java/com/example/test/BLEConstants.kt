package com.example.test

object BLEConstants {

    const val MANUFACTURER_ID = 0x1234

    const val PROTOCOL_VERSION: Byte = 0x01

    // Packet types
    const val PACKET_TYPE_HELLO: Byte = 0x01
    const val PACKET_TYPE_DATA: Byte = 0x02

    // Broadcast node ID
    const val BROADCAST_NODE_ID = -1

    // Fixed mesh header size:
    // version(1) + type(1) + src(4) + dest(4) + ttl(1) + payloadLen(1)
    const val HEADER_SIZE = 12

    // Max payload for BLE safety (stay small)
    const val MAX_PAYLOAD_SIZE = 10

    // Total packet size = header + payload
    const val MAX_PACKET_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE
}
