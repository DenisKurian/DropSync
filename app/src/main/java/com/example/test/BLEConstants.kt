package com.example.test

object BLEConstants {

    const val MANUFACTURER_ID = 0x1234

    const val PROTOCOL_VERSION: Byte = 0x01

    /* ================= Packet Types ================= */

    const val PACKET_TYPE_HELLO: Byte = 0x01
    const val PACKET_TYPE_DATA: Byte = 0x02
    const val PACKET_TYPE_ACK: Byte = 0x03

    const val PACKET_TYPE_FILE_REQUEST: Byte = 0x07
    const val PACKET_TYPE_ROUTE_REPLY: Byte = 0x08

    /* ================= Mesh ================= */

    const val BROADCAST_NODE_ID = -1

    /*
        Header structure:
        version(1)
        type(1)
        packetId(4)
        src(4)
        dest(4)
        ttl(1)
        payloadLen(1)

        TOTAL = 16 bytes
     */

    const val HEADER_SIZE = 16

    /* ================= Payload ================= */

    // Reduced for BLE advertisement safety
    const val MAX_PAYLOAD_SIZE = 64

    const val MAX_PACKET_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE

    /* ================= Defaults ================= */

    const val DEFAULT_TTL: Byte = 3
}