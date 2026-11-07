package fr.hybridbridge.app

import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BitchatProtocolTest {
    private fun packet(payload: ByteArray, type: MessageType = MessageType.MESSAGE): BitchatPacket = BitchatPacket(
        version = 1u,
        type = type.value,
        senderID = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
        recipientID = SpecialRecipients.BROADCAST.copyOf(),
        timestamp = 123uL,
        payload = payload,
        ttl = 3u
    )

    @Test
    fun officialCodecRoundTripsPublicPacket() {
        val original = packet("codec test".toByteArray())
        val encoded = original.toBinaryData(padding = false)
        assertNotNull(encoded)
        val decoded = BitchatPacket.fromBinaryData(encoded!!)
        assertNotNull(decoded)
        assertEquals(original.type, decoded!!.type)
        assertEquals(original.timestamp, decoded.timestamp)
        assertEquals(original.ttl, decoded.ttl)
        assertArrayEquals(original.senderID, decoded.senderID)
        assertArrayEquals(original.recipientID, decoded.recipientID)
        assertArrayEquals(original.payload, decoded.payload)
    }

    @Test
    fun rejectsEmptyWirePacket() {
        assertNull(BitchatPacket.fromBinaryData(byteArrayOf()))
    }

    @Test
    fun rejectsTruncatedHeader() {
        assertNull(BitchatPacket.fromBinaryData(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun rejectsTruncatedPayload() {
        val encoded = packet("codec test".toByteArray()).toBinaryData(padding = false)!!
        assertNull(BitchatPacket.fromBinaryData(encoded.copyOf(encoded.size - 1)))
    }

    @Test
    fun preservesReceivedWireRepresentation() {
        val original = packet(ByteArray(4096) { 65 }, MessageType.FILE_TRANSFER)
        val encoded = original.toBinaryData(padding = false)!!
        val decoded = BitchatPacket.fromBinaryData(encoded)
        assertNotNull(decoded)
        assertArrayEquals(original.payload, decoded!!.payload)
        assertArrayEquals(encoded, decoded.toBinaryData(padding = false))
    }
}
