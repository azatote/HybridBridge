package fr.hybridbridge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.meshtastic.proto.MeshProtos

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublicBridgeTest {
    private var now = 0L
    private val sent = arrayListOf<String>()
    private fun bridge() = PublicBridge({ sent.add(it); Result.success(Unit) }, { now })
    private fun message(id: Int = 1, sender: Int = 42, channel: Int = 1, text: String = "/hb test", outgoing: Boolean = false) =
        MeshText(id, sender, channel, text, outgoing, "Recu du Heltec")

    @Test fun disabledByDefault() {
        val bridge = bridge(); bridge.receive(message()); bridge.drain()
        assertFalse(bridge.enabled); assertTrue(sent.isEmpty())
    }

    @Test fun requiresAuthorizedChannelAndSenderOptIn() {
        val bridge = bridge(); bridge.enable(1, 99)
        bridge.receive(message(channel = 0)); bridge.receive(message(text = "conversation locale"))
        bridge.receive(message(sender = 99)); bridge.receive(message(outgoing = true)); bridge.drain()
        assertTrue(sent.isEmpty())
    }

    @Test fun attributesForwardedTextWithoutImpersonatingSender() {
        val bridge = bridge(); bridge.enable(1, 99); bridge.receive(message()); bridge.drain()
        assertEquals(1, sent.size); assertEquals("[HB !0000002a:1] test", sent.single())
    }

    @Test fun deduplicatesAndPersistsIdsWithoutMessageContent() {
        var persisted: Set<String> = emptySet()
        val bridge = PublicBridge({ sent.add(it); Result.success(Unit) }, { now }, persistSeen = { persisted = it })
        bridge.enable(1, 99); bridge.receive(message()); bridge.receive(message()); bridge.drain()
        assertEquals(1, sent.size); assertEquals(setOf("2a:1"), persisted)
        val restarted = PublicBridge({ sent.add(it); Result.success(Unit) }, { now }, persisted)
        restarted.enable(1, 99); restarted.receive(message()); restarted.drain()
        assertEquals(1, sent.size)
    }

    @Test fun preventsRebridgingAndOversizedUtf8() {
        val bridge = bridge(); bridge.enable(1, 99)
        bridge.receive(message(text = "/hb [HB !other:1] echo"))
        bridge.receive(message(id = 2, text = "/hb " + "\u00e9".repeat(100))); bridge.drain()
        assertTrue(sent.isEmpty()); assertEquals(2, bridge.rejected)
    }

    @Test fun boundsQueueAndExpiresUnavailableDestination() {
        val bridge = PublicBridge({ Result.failure(IllegalStateException()) }, { now })
        bridge.enable(1, 99); for (id in 1..9) bridge.receive(message(id = id))
        assertEquals(8, bridge.pendingCount); assertEquals(1, bridge.rejected)
        bridge.drain(); now = 30_000; bridge.drain()
        assertEquals(0, bridge.pendingCount); assertEquals(9, bridge.rejected)
    }

    @Test fun rateLimitsAndStopsImmediately() {
        val bridge = bridge(); bridge.enable(1, 99); bridge.receive(message()); bridge.receive(message(id = 2))
        bridge.drain(); bridge.drain(); assertEquals(1, sent.size)
        now = 5_000; bridge.drain(); assertEquals(2, sent.size)
        bridge.receive(message(id = 3)); bridge.disable(); now = 10_000; bridge.drain()
        assertEquals(2, sent.size); assertEquals(0, bridge.pendingCount)
    }

    @Test fun simulatedHeltecFrameBecomesSignedBitchatPublicText() {
        val fixedTime = 1_700_000_000_000L
        val gateway = BitchatIdentity.generateInMemory()
        val receiver = BitchatPublicProtocol(BitchatIdentity.generateInMemory()) { fixedTime }
        receiver.receive(gateway.announcement(fixedTime).toBinaryData()!!)
        val bridge = PublicBridge({ text ->
            val received = receiver.receive(gateway.publicText(text, fixedTime).toBinaryData()!!)
            if (received != null) { sent.add(received.text); Result.success(Unit) }
            else Result.failure(IllegalStateException("Paquet Bitchat refuse"))
        }, { now })
        val radioPacket = MeshProtos.ToRadio.parseFrom(
            MeshtasticProtocol.channelText(42, 1, 123, "/hb parcours test")
        ).packet
        val frame = MeshProtos.FromRadio.newBuilder().setPacket(radioPacket).build().toByteArray()
        val receivedMesh = MeshtasticProtocol.receivedText(MeshtasticProtocol.decode(frame).packet)!!
        bridge.enable(1, 99); bridge.receive(receivedMesh); bridge.drain()
        assertEquals(listOf("[HB !0000002a:7b] parcours test"), sent)
    }

    @Test fun directedRadioMessageNeverReachesBridge() {
        val radioPacket = MeshProtos.ToRadio.parseFrom(
            MeshtasticProtocol.channelText(42, 1, 123, "/hb prive")
        ).packet.toBuilder().setTo(99).build()
        val message = MeshtasticProtocol.receivedText(radioPacket)
        val bridge = bridge(); bridge.enable(1, 99)
        if (message != null) bridge.receive(message)
        bridge.drain(); assertTrue(sent.isEmpty())
    }
}
