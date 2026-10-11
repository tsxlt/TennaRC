package lt.tsx.tennarc

import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportEnvelopeTest {
    @Test
    fun `a sealed report opens with the server's key`() {
        val sealed = ReportEnvelope.seal("The volume key sticks".encodeToByteArray(), TEST_PUBLIC)
        assertTrue(sealed.startsWith(ReportEnvelope.PREFIX))
        assertEquals("The volume key sticks", open(sealed, TEST_SECRET).decodeToString())
        File("build/report-envelope-vector.txt").writeText(ReportEnvelope.seal(VECTOR.encodeToByteArray(), TEST_PUBLIC))
    }

    @Test
    fun `each report gets its own one-off key`() {
        val a = ReportEnvelope.seal(byteArrayOf(1), TEST_PUBLIC)
        val b = ReportEnvelope.seal(byteArrayOf(1), TEST_PUBLIC)
        assertTrue(a.substring(7, 95) != b.substring(7, 95))
    }

    companion object {
        const val TEST_SECRET = "TAv43Ku3v1gj+YzWZmHTEre5jhUCWKDKtXTMUE0ztIk="
        const val TEST_PUBLIC = "BK1+r9w9Q9fZg2g4U7BZMMhnd7777rLQDbJCQYCqHkiogtD/R19IrO6/KZqR1q/q8AbElmj5JhgGYoNsKtoj2MU="
        const val VECTOR = "sealed by TennaRC"

        fun open(sealed: String, secret: String): ByteArray {
            val raw = Base64.getDecoder().decode(sealed.removePrefix(ReportEnvelope.PREFIX))
            val theirs = raw.copyOfRange(0, 65)
            val nonce = raw.copyOfRange(65, 77)
            val curve = (KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair().public as ECPublicKey).params
            val factory = KeyFactory.getInstance("EC")
            val private = factory.generatePrivate(ECPrivateKeySpec(BigInteger(1, Base64.getDecoder().decode(secret)), curve))
            val point = java.security.spec.ECPoint(BigInteger(1, theirs.copyOfRange(1, 33)), BigInteger(1, theirs.copyOfRange(33, 65)))
            val their = factory.generatePublic(java.security.spec.ECPublicKeySpec(point, curve))
            val shared = KeyAgreement.getInstance("ECDH").run {
                init(private)
                doPhase(their, true)
                generateSecret()
            }
            val key = ReportEnvelope.hkdf(salt = theirs + Base64.getDecoder().decode(TEST_PUBLIC), secret = shared)
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(ReportEnvelope.PREFIX.encodeToByteArray())
                doFinal(raw.copyOfRange(77, raw.size))
            }
        }
    }
}
