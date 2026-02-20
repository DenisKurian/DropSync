package com.example.test

object BLEConstants {

    const val MANUFACTURER_ID = 0x1234

    const val PROTOCOL_VERSION: Byte = 0x01

    // Packet types
    const val PACKET_TYPE_HELLO: Byte = 0x01
    const val PACKET_TYPE_DATA: Byte = 0x02
    const val PACKET_TYPE_ACK: Byte = 0x03

    // Broadcast node ID
    const val BROADCAST_NODE_ID = -1

    // Mesh header structure:
    // version(1)
    // type(1)
    // packetId(4)
    // src(4)
    // dest(4)
    // ttl(1)
    // payloadLen(1)
    //
    // TOTAL = 16 bytes
    const val HEADER_SIZE = 16

    // Max payload for BLE safety
    const val MAX_PAYLOAD_SIZE = 10

    // Total packet size = header + payload
    const val MAX_PACKET_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE

    // Default TTL for new packets
    const val DEFAULT_TTL: Byte = 3
}
