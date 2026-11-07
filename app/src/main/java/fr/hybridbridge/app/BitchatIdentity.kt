package fr.hybridbridge.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.bitchat.android.model.IdentityAnnouncement
import com.bitchat.android.model.PeerCapabilities
import com.bitchat.android.noise.NoisePeerIdentity
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import com.bitchat.android.util.AppConstants
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BitchatIdentity private constructor(noiseSecret: ByteArray, signingSecret: ByteArray) {
    private val noiseKey = X25519PrivateKeyParameters(noiseSecret, 0)
    private val signingKey = Ed25519PrivateKeyParameters(signingSecret, 0)
    val peerId: String = requireNotNull(NoisePeerIdentity.derivePeerID(noiseKey.generatePublicKey().encoded))
    val senderId: ByteArray get() = peerId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun announcement(now: Long = System.currentTimeMillis()): BitchatPacket {
        val payload = requireNotNull(IdentityAnnouncement(
            "HybridBridge", noiseKey.generatePublicKey().encoded,
            signingKey.generatePublicKey().encoded, PeerCapabilities.NONE
        ).encode())
        return signedPacket(MessageType.ANNOUNCE, payload, now)
    }

    fun publicText(text: String, now: Long = System.currentTimeMillis()): BitchatPacket {
        val payload = text.toByteArray(Charsets.UTF_8)
        require(text.isNotBlank() && payload.size <= 120) { "Texte limite a 120 octets UTF-8" }
        return signedPacket(MessageType.MESSAGE, payload, now)
    }

    private fun signedPacket(type: MessageType, payload: ByteArray, now: Long): BitchatPacket {
        val packet = BitchatPacket(
            version = 1u, type = type.value, senderID = senderId,
            recipientID = SpecialRecipients.BROADCAST.copyOf(), timestamp = now.toULong(),
            payload = payload, ttl = AppConstants.MESSAGE_TTL_HOPS
        )
        val canonical = requireNotNull(packet.toBinaryDataForSigning())
        val signer = Ed25519Signer().apply { init(true, signingKey); update(canonical, 0, canonical.size) }
        return packet.copy(signature = signer.generateSignature())
    }

    companion object {
        private const val KEY_ALIAS = "hybridbridge-bitchat-identity-v1"
        private val contextBytes = "HybridBridge-Bitchat-Identity-v1".toByteArray(Charsets.UTF_8)

        fun verify(signature: ByteArray, data: ByteArray, publicKey: ByteArray): Boolean {
            if (signature.size != 64 || publicKey.size != 32) return false
            return try {
                Ed25519Signer().apply {
                    init(false, Ed25519PublicKeyParameters(publicKey, 0))
                    update(data, 0, data.size)
                }.verifySignature(signature)
            } catch (error: IllegalArgumentException) { false }
        }

        internal fun generateInMemory(): BitchatIdentity {
            val random = SecureRandom()
            return BitchatIdentity(X25519PrivateKeyParameters(random).encoded, Ed25519PrivateKeyParameters(random).encoded)
        }

        fun load(context: Context): BitchatIdentity {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
            val file = File(context.noBackupFilesDir, "bitchat-identity-v1.bin")
            val persisted = file.exists() || File(file.path + ".bak").exists()
            check(!persisted || existing != null) { "Cle d'identite locale indisponible" }
            val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
            val atomic = AtomicFile(file)
            val secrets = if (persisted) {
                val encoded = atomic.openRead().use { it.readBytes() }
                require(encoded.size == 92) { "Identite locale de taille invalide" }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encoded.copyOfRange(0, 12)))
                    updateAAD(contextBytes)
                }
                cipher.doFinal(encoded.copyOfRange(12, encoded.size))
            } else {
                val random = SecureRandom()
                val generated = X25519PrivateKeyParameters(random).encoded + Ed25519PrivateKeyParameters(random).encoded
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, key); updateAAD(contextBytes)
                }
                val encrypted = cipher.iv + cipher.doFinal(generated)
                val output = atomic.startWrite()
                try { output.write(encrypted); atomic.finishWrite(output) }
                catch (error: Exception) { atomic.failWrite(output); generated.fill(0); throw error }
                generated
            }
            try {
                require(secrets.size == 64) { "Identite locale invalide" }
                return BitchatIdentity(secrets.copyOfRange(0, 32), secrets.copyOfRange(32, 64))
            } finally { secrets.fill(0) }
        }
    }
}
