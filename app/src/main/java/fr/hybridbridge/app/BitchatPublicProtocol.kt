package fr.hybridbridge.app

import com.bitchat.android.mesh.AnnouncementIdentityValidator
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import com.bitchat.android.util.toHexString
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

data class BitchatPeer(val id: String, val name: String)
data class BitchatPublicText(val id: String, val sender: String, val name: String, val text: String)

class BitchatPublicProtocol(private val identity: BitchatIdentity, private val now: () -> Long = System::currentTimeMillis) {
    private data class Peer(val name: String, val signingKey: ByteArray)
    private val peers = linkedMapOf<String, Peer>()
    private val seen = linkedSetOf<String>()

    fun peers(): List<BitchatPeer> = peers.map { BitchatPeer(it.key, it.value.name) }

    fun announcement(): ByteArray = requireNotNull(identity.announcement(now()).toBinaryData())

    fun outgoing(text: String): Pair<ByteArray, BitchatPublicText> {
        val packet = identity.publicText(text, now())
        val bytes = requireNotNull(packet.toBinaryData())
        require(bytes.size <= 512) { "Paquet BLE trop long" }
        return bytes to BitchatPublicText(messageId(packet), identity.peerId, "Moi", text)
    }

    fun receive(bytes: ByteArray): BitchatPublicText? {
        if (bytes.size !in 14..512) return null
        val type = MessageType.fromValue(bytes[1].toUByte())
        if (type != MessageType.ANNOUNCE && type != MessageType.MESSAGE) return null
        val packet = BitchatPacket.fromBinaryData(bytes) ?: return null
        val sender = packet.senderID.toHexString()
        if (sender == identity.peerId || packet.senderID.size != 8) return null
        val clock = now().coerceAtLeast(0).toULong()
        val skew = if (packet.timestamp > clock) packet.timestamp - clock else clock - packet.timestamp
        if (skew > 600_000uL) return null
        val broadcast = packet.recipientID == null || packet.recipientID.contentEquals(SpecialRecipients.BROADCAST)
        if (type == MessageType.ANNOUNCE) {
            if (!broadcast && !packet.recipientID.contentEquals(identity.senderId)) return null
            val announcement = AnnouncementIdentityValidator.verify(packet, sender, now(), BitchatIdentity::verify) ?: return null
            val existing = peers[sender]
            if (existing != null && !existing.signingKey.contentEquals(announcement.signingPublicKey)) return null
            if (existing == null && peers.size >= 64) return null
            peers[sender] = Peer(announcement.nickname.filter { !it.isISOControl() }.take(40).ifBlank { sender.take(8) }, announcement.signingPublicKey.copyOf())
            return null
        }
        if (!broadcast || packet.payload.size !in 1..4096) return null
        val peer = peers[sender] ?: return null
        val signature = packet.signature ?: return null
        val canonical = packet.toBinaryDataForSigning() ?: return null
        if (!BitchatIdentity.verify(signature, canonical, peer.signingKey)) return null
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(packet.payload)).toString()
        } catch (error: java.nio.charset.CharacterCodingException) { return null }
        if (text.isBlank()) return null
        val id = messageId(packet)
        if (!seen.add(id)) return null
        if (seen.size > 256) seen.remove(seen.first())
        return BitchatPublicText(id, sender, peer.name, text)
    }

    private fun messageId(packet: BitchatPacket): String =
        packet.senderID.toHexString() + ":" + MessageDigest.getInstance("SHA-256").digest(requireNotNull(packet.signature)).toHexString()
}
