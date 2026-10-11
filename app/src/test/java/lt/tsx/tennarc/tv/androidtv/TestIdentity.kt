package lt.tsx.tennarc.tv.androidtv

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class TestIdentity(commonName: String) : ClientIdentity {
    private val keys: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    override val privateKey: PrivateKey = keys.private
    override val certificate: X509Certificate = selfSigned(commonName, keys)

    private companion object {
        val SHA256_WITH_RSA = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)
        val COMMON_NAME = byteArrayOf(0x55, 0x04, 0x03)

        fun selfSigned(commonName: String, keys: KeyPair): X509Certificate {
            val algorithm = der(0x30, der(0x06, SHA256_WITH_RSA), byteArrayOf(0x05, 0x00))
            val name = der(0x30, der(0x31, der(0x30, der(0x06, COMMON_NAME), der(0x0C, commonName.encodeToByteArray()))))
            val validity = der(0x30, der(0x17, "250101000000Z".encodeToByteArray()), der(0x17, "491231235959Z".encodeToByteArray()))
            val tbs = der(
                0x30,
                der(0xA0, der(0x02, byteArrayOf(2))),
                der(0x02, BigInteger.valueOf(System.nanoTime() and Long.MAX_VALUE).toByteArray()),
                algorithm,
                name,
                validity,
                name,
                keys.public.encoded,
            )
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(keys.private)
                update(tbs)
                sign()
            }
            val certificate = der(0x30, tbs, algorithm, der(0x03, byteArrayOf(0) + signature))
            return CertificateFactory.getInstance("X.509").generateCertificate(certificate.inputStream()) as X509Certificate
        }

        fun der(tag: Int, vararg parts: ByteArray): ByteArray {
            val body = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
            val length = when {
                body.size < 0x80 -> byteArrayOf(body.size.toByte())
                body.size < 0x100 -> byteArrayOf(0x81.toByte(), body.size.toByte())
                else -> byteArrayOf(0x82.toByte(), (body.size shr 8).toByte(), body.size.toByte())
            }
            return byteArrayOf(tag.toByte()) + length + body
        }
    }
}
