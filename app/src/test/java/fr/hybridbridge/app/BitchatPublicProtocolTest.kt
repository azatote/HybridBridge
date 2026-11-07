package fr.hybridbridge.app

import com.bitchat.android.mesh.AnnouncementIdentityValidator
import com.bitchat.android.protocol.BitchatPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BitchatPublicProtocolTest {
    private val now = 1_700_000_000_000L

    @Test
    fun localAnnouncementPassesOfficialValidator() {
        val identity = BitchatIdentity.generateInMemory()
        assertNotNull(AnnouncementIdentityValidator.verify(identity.announcement(now), identity.peerId, now, BitchatIdentity::verify))
    }

    @Test
    fun rejectsChangedPeerId() {
        val identity = BitchatIdentity.generateInMemory()
        val packet = identity.announcement(now).copy(senderID = ByteArray(8))
        assertNull(AnnouncementIdentityValidator.verify(packet, "0000000000000000", now, BitchatIdentity::verify))
    }

    @Test
    fun receivesSignedPublicTextAfterAnnouncement() {
        val sender = BitchatIdentity.generateInMemory()
        val receiver = BitchatPublicProtocol(BitchatIdentity.generateInMemory()) { now }
        receiver.receive(sender.announcement(now).toBinaryData()!!)
        val message = receiver.receive(sender.publicText("test public", now).toBinaryData()!!)
        assertNotNull(message)
        assertEquals("test public", message!!.text)
        assertEquals(1, receiver.peers().size)
    }

    @Test
    fun rejectsMessageBeforeAnnouncement() {
        val sender = BitchatIdentity.generateInMemory()
        val receiver = BitchatPublicProtocol(BitchatIdentity.generateInMemory()) { now }
        assertNull(receiver.receive(sender.publicText("test", now).toBinaryData()!!))
    }

    @Test
    fun rejectsModifiedSignatureAndReplay() {
        val sender = BitchatIdentity.generateInMemory()
        val receiver = BitchatPublicProtocol(BitchatIdentity.generateInMemory()) { now }
        receiver.receive(sender.announcement(now).toBinaryData()!!)
        val packet = sender.publicText("test", now)
        assertNull(receiver.receive(packet.copy(signature = ByteArray(64)).toBinaryData()!!))
        val encoded = packet.toBinaryData()!!
        assertNotNull(receiver.receive(encoded))
        assertNull(receiver.receive(encoded))
    }

    @Test
    fun rejectsDirectedAndStaleMessages() {
        val sender = BitchatIdentity.generateInMemory()
        val receiver = BitchatPublicProtocol(BitchatIdentity.generateInMemory()) { now }
        receiver.receive(sender.announcement(now).toBinaryData()!!)
        assertNull(receiver.receive(sender.publicText("test", now).copy(recipientID = ByteArray(8)).toBinaryData()!!))
        assertNull(receiver.receive(sender.publicText("old", now - 700_000).toBinaryData()!!))
    }

    @Test(expected = IllegalArgumentException::class)
    fun limitsUtf8MessageSize() {
        BitchatIdentity.generateInMemory().publicText("\u00e9".repeat(61), now)
    }
}
