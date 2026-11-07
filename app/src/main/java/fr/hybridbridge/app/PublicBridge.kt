package fr.hybridbridge.app

import java.util.ArrayDeque

class PublicBridge(
    private val sendPublic: (String) -> Result<Unit>,
    private val clock: () -> Long,
    initialSeen: Set<String> = emptySet(),
    private val persistSeen: (Set<String>) -> Unit = {}
) {
    private data class Pending(val id: String, val text: String, val expiresAt: Long)
    private val queue = ArrayDeque<Pending>()
    private val seen = LinkedHashSet(initialSeen.takeLastEntries(512))
    private var channel: Int? = null
    private var localNode: Int? = null
    private var lastSend = -5_000L
    var enabled: Boolean = false
        private set
    var forwarded: Int = 0
        private set
    var rejected: Int = 0
        private set
    var lastEvent: String = "Desactive"
        private set
    val pendingCount: Int get() = queue.size

    fun enable(channelIndex: Int, connectedNode: Int) {
        require(channelIndex in 0..7 && connectedNode != 0 && connectedNode != -1)
        queue.clear()
        channel = channelIndex
        localNode = connectedNode
        enabled = true
        lastEvent = "Canal $channelIndex autorise ; prefixe /hb requis"
    }

    fun disable() {
        enabled = false
        channel = null
        localNode = null
        queue.clear()
        lastEvent = "Desactive"
    }

    fun receive(message: MeshText) {
        if (!enabled || message.outgoing || message.channel != channel || message.sender == localNode) return
        if (!message.text.startsWith("/hb ")) return
        val id = "${message.sender.toUInt().toString(16)}:${message.id.toUInt().toString(16)}"
        if (message.id == 0 || message.sender == 0 || message.sender == -1 || seen.contains(id)) return
        val payload = message.text.removePrefix("/hb ").trim()
        if (payload.isBlank() || payload.contains("[HB ") || payload.any { it.isISOControl() && it != '\n' && it != '\t' }) {
            reject("Contenu refuse")
            return
        }
        val text = "[HB ${MeshtasticProtocol.nodeLabel(message.sender)}:${message.id.toUInt().toString(16)}] $payload"
        if (text.toByteArray(Charsets.UTF_8).size > 120) { reject("Texte trop long pour Bitchat"); return }
        if (queue.size >= 8) { reject("File pleine"); return }
        seen.add(id)
        if (seen.size > 512) seen.remove(seen.first())
        try {
            persistSeen(seen.toSet())
        } catch (error: Exception) {
            seen.remove(id)
            reject("Historique de doublons indisponible")
            return
        }
        queue.add(Pending(id, text, clock() + 30_000))
        lastEvent = "En attente Bitchat"
    }

    fun drain() {
        if (!enabled) return
        val now = clock()
        while (queue.isNotEmpty() && queue.peekFirst().expiresAt <= now) {
            queue.removeFirst()
            reject("Message expire")
        }
        if (queue.isEmpty() || now - lastSend < 5_000) return
        val next = queue.peekFirst()
        val result = try { sendPublic(next.text) } catch (error: Exception) { Result.failure(error) }
        lastSend = now
        if (result.isSuccess) {
            queue.removeFirst()
            forwarded++
            lastEvent = "Remis a la file BLE ; reception distante a verifier"
        } else lastEvent = "Bitchat indisponible ; attente bornee"
    }

    private fun reject(reason: String) { rejected++; lastEvent = reason }

    private fun Set<String>.takeLastEntries(limit: Int): List<String> = toList().takeLast(limit)
}
