package lt.tsx.tennarc

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object ReportEnvelope {
    const val PREFIX = "tenna1."
    private val INFO = "tenna.tsx.lt report v1".encodeToByteArray()
    private const val NONCE = 12

    fun seal(plaintext: ByteArray, serverKey: String, random: SecureRandom = SecureRandom()): String {
        val ours = Base64.getDecoder().decode(serverKey)
        require(ours.size == 65 && ours[0] == 4.toByte()) { "Not an uncompressed P-256 point" }
        val oneOff = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()
        val curve = (oneOff.public as ECPublicKey).params
        val point = ECPoint(BigInteger(1, ours.copyOfRange(1, 33)), BigInteger(1, ours.copyOfRange(33, 65)))
        val server = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, curve))
        val shared = KeyAgreement.getInstance("ECDH").run {
            init(oneOff.private)
            doPhase(server, true)
            generateSecret()
        }
        val theirs = uncompressed(oneOff.public as ECPublicKey)
        val key = hkdf(salt = theirs + ours, secret = shared)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val sealed = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(PREFIX.encodeToByteArray())
            doFinal(plaintext)
        }
        return PREFIX + Base64.getEncoder().encodeToString(theirs + nonce + sealed)
    }

    internal fun uncompressed(key: ECPublicKey): ByteArray =
        byteArrayOf(4) + fixed(key.w.affineX) + fixed(key.w.affineY)

    private fun fixed(n: BigInteger): ByteArray = n.toByteArray().takeLast(32).toByteArray().let { ByteArray(32 - it.size) + it }

    internal fun hkdf(salt: ByteArray, secret: ByteArray): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }.doFinal(secret)
        return Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(extract, "HmacSHA256")) }.doFinal(INFO + byteArrayOf(1))
    }
}
