package lt.tsx.tennarc.tv.androidtv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger
import java.security.interfaces.RSAPublicKey

class PairingTest {
    @Test
    fun `the pairing request names the service and the phone`() {
        assertArrayEquals(
            bytes(8, 2, 16, 200, 1, 82, 26, 10, 9, "atvremote", 18, 13, "interface web"),
            Polo.pairingRequest("interface web"),
        )
    }

    @Test
    fun `options and configuration ask for six hex digits typed in`() {
        assertArrayEquals(bytes(8, 2, 16, 200, 1, 162, 1, 8, 10, 4, 8, 3, 16, 6, 24, 1), Polo.options())
        assertArrayEquals(bytes(8, 2, 16, 200, 1, 242, 1, 8, 10, 4, 8, 3, 16, 6, 16, 1), Polo.configuration())
    }

    @Test
    fun `the secret goes in its own message`() {
        val secret = ByteArray(32) { it.toByte() }
        assertArrayEquals(bytes(8, 2, 16, 200, 1, 194, 2, 34, 10, 32, secret), Polo.secret(secret))
    }

    @Test
    fun `the TV's replies are told apart by which message they carry`() {
        Polo.parse(bytes(8, 2, 16, 200, 1, 90, 0)).let {
            assertEquals(200, it.status)
            assertEquals(Polo.Step.PAIRING_REQUEST_ACK, it.step)
        }
        assertEquals(Polo.Step.OPTIONS, Polo.parse(bytes(8, 2, 16, 200, 1, 162, 1, 8, 18, 4, 8, 3, 16, 6, 24, 1)).step)
        assertEquals(Polo.Step.CONFIGURATION_ACK, Polo.parse(bytes(8, 2, 16, 200, 1, 250, 1, 0)).step)
        assertEquals(Polo.Step.SECRET_ACK, Polo.parse(bytes(8, 2, 16, 200, 1, 202, 2, 2, 10, 0)).step)
        Polo.parse(bytes(8, 2, 16, 146, 3)).let {
            assertEquals(402, it.status)
            assertEquals(Polo.Step.UNKNOWN, it.step)
        }
    }

    private val client = rsa(BigInteger.TWO.pow(1023) + BigInteger.TWO.pow(512) + BigInteger.valueOf(12345))
    private val server = rsa(BigInteger.TWO.pow(1023) + BigInteger.valueOf(3).pow(500))
    private val expected = "77c6ae94f4d456c6bc4ba228c01fa1c19902ef9570b7365c8938653189438b42".hexToByteArray()

    @Test
    fun `the secret matches androidtvremote2's`() {
        assertArrayEquals(expected, pairingSecret(client, server, "77ABCD"))
        assertArrayEquals(expected, pairingSecret(client, server, "77abcd"))
    }

    @Test
    fun `a code whose check digits don't match is caught before sending`() {
        assertNull(pairingSecret(client, server, "78ABCD"))
        assertNull(pairingSecret(client, server, "77ABCE"))
    }

    @Test
    fun `only six hex digits make a code`() {
        assertNull(pairingSecret(client, server, "77ABC"))
        assertNull(pairingSecret(client, server, "77ABCG"))
        assertNull(pairingSecret(client, server, "77ABCDE"))
    }

    private fun rsa(modulus: BigInteger, exponent: BigInteger = BigInteger.valueOf(65537)) = object : RSAPublicKey {
        override fun getModulus() = modulus
        override fun getPublicExponent() = exponent
        override fun getAlgorithm() = "RSA"
        override fun getFormat(): String? = null
        override fun getEncoded(): ByteArray? = null
    }
}
