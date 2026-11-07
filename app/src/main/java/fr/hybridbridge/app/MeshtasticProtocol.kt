package fr.hybridbridge.app

import com.google.protobuf.ByteString
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.Portnums
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class MeshChannel(val index: Int, val name: String)

data class MeshNode(
    val number: Int,
    val name: String,
    val firmware: String,
    val channels: List<MeshChannel>,
    val battery: Int? = null
)

data class MeshText(
    val id: Int,
    val sender: Int,
    val channel: Int,
    val text: String,
    val outgoing: Boolean,
    val state: String
)

object MeshtasticProtocol {
    const val MAX_TEXT_BYTES = 180
    const val MAX_GATT_BYTES = 512

    fun configurationRequest(requestId: Int): ByteArray {
        require(requestId != 0) { "Identifiant de synchronisation invalide" }
        return MeshProtos.ToRadio.newBuilder().setWantConfigId(requestId).build().toByteArray()
    }

    fun channelText(sender: Int, channel: Int, packetId: Int, text: String): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)
        require(text.isNotBlank()) { "Message vide" }
        require(payload.size <= MAX_TEXT_BYTES) { "Message limite a $MAX_TEXT_BYTES octets UTF-8" }
        require(channel in 0..7) { "Canal invalide" }
        require(packetId != 0) { "Identifiant du message invalide" }
        val data = MeshProtos.Data.newBuilder()
            .setPortnum(Portnums.PortNum.TEXT_MESSAGE_APP)
            .setPayload(ByteString.copyFrom(payload))
            .build()
        val packet = MeshProtos.MeshPacket.newBuilder()
            .setFrom(sender)
            .setTo(-1)
            .setChannel(channel)
            .setId(packetId)
            .setHopLimit(3)
            .setWantAck(false)
            .setDecoded(data)
            .build()
        return MeshProtos.ToRadio.newBuilder().setPacket(packet).build().toByteArray()
    }

    fun decode(bytes: ByteArray): MeshProtos.FromRadio {
        require(bytes.isNotEmpty() && bytes.size <= MAX_GATT_BYTES) { "Paquet GATT de taille invalide" }
        return MeshProtos.FromRadio.parseFrom(bytes)
    }

    fun receivedText(packet: MeshProtos.MeshPacket): MeshText? {
        if (!packet.hasDecoded() || packet.to != -1 || packet.id == 0 || packet.channel !in 0..7) return null
        val data = packet.decoded
        if (data.portnum != Portnums.PortNum.TEXT_MESSAGE_APP || data.payload.size() > 233) return null
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data.payload.toByteArray())).toString()
        } catch (error: java.nio.charset.CharacterCodingException) {
            return null
        }
        if (text.isBlank()) return null
        return MeshText(packet.id, packet.from, packet.channel, text, false, "Recu du Heltec")
    }

    fun nodeLabel(number: Int): String = "!" + number.toUInt().toString(16).padStart(8, '0')
}
