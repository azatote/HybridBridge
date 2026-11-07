package fr.hybridbridge.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MeshtasticGattClientTest {
    @Test
    fun decodesLittleEndianCounter() {
        assertEquals(0x04030201L, MeshtasticGattClient.decodeFromNum(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun preservesUnsignedCounter() {
        assertEquals(0xffffffffL, MeshtasticGattClient.decodeFromNum(byteArrayOf(-1, -1, -1, -1)))
    }

    @Test
    fun acceptsZeroCounter() {
        assertEquals(0L, MeshtasticGattClient.decodeFromNum(byteArrayOf(0, 0, 0, 0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedCounter() {
        MeshtasticGattClient.decodeFromNum(byteArrayOf(1, 2, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedCounter() {
        MeshtasticGattClient.decodeFromNum(byteArrayOf(1, 2, 3, 4, 5))
    }
}
