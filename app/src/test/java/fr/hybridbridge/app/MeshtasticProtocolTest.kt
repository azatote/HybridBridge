package fr.hybridbridge.app

import com.google.protobuf.ByteString
import org.junit.Assert.*
import org.junit.Test
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.Portnums

class MeshtasticProtocolTest {
    @Test
    fun configurationRequestCarriesItsIdentifier() {
        val request = MeshProtos.ToRadio.parseFrom(MeshtasticProtocol.configurationRequest(123))
        assertEquals(123, request.wantConfigId)
        assertFalse(request.hasPacket())
    }

    @Test
    fun createsChannelBroadcastWithoutDeliveryClaim() {
        val packet = MeshProtos.ToRadio.parseFrom(MeshtasticProtocol.channelText(42, 1, 123, "test")).packet
        assertEquals(-1, packet.to)
        assertEquals(1, packet.channel)
        assertEquals(123, packet.id)
        assertEquals(3, packet.hopLimit)
        assertFalse(packet.wantAck)
        assertEquals(Portnums.PortNum.TEXT_MESSAGE_APP, packet.decoded.portnum)
        assertEquals("test", packet.decoded.payload.toStringUtf8())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankText() {
        MeshtasticProtocol.channelText(42, 0, 1, "  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun countsUtf8BytesRatherThanCharacters() {
        MeshtasticProtocol.channelText(42, 0, 1, "\u00e9".repeat(91))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidChannel() {
        MeshtasticProtocol.channelText(42, 8, 1, "test")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroPacketId() {
        MeshtasticProtocol.channelText(42, 0, 0, "test")
    }

    @Test
    fun rejectsDirectMessages() {
        val packet = MeshProtos.ToRadio.parseFrom(MeshtasticProtocol.channelText(42, 0, 1, "test")).packet
            .toBuilder().setTo(123).build()
        assertNull(MeshtasticProtocol.receivedText(packet))
    }

    @Test
    fun rejectsOpaqueEncryptedPackets() {
        val packet = MeshProtos.MeshPacket.newBuilder().setTo(-1).setId(1)
            .setEncrypted(ByteString.copyFromUtf8("opaque")).build()
        assertNull(MeshtasticProtocol.receivedText(packet))
    }

    @Test
    fun receivesValidBroadcastText() {
        val packet = MeshProtos.ToRadio.parseFrom(MeshtasticProtocol.channelText(42, 0, 1, "test")).packet
        val message = MeshtasticProtocol.receivedText(packet)
        assertNotNull(message)
        assertEquals("test", message!!.text)
        assertFalse(message.outgoing)
    }
}
