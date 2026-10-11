package lt.tsx.tennarc.tv.androidtv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

interface ClientIdentity {
    val certificate: X509Certificate
    val privateKey: PrivateKey
}

class KeystoreIdentity(private val alias: String = "androidtv-remote") : ClientIdentity {
    private val entry: KeyStore.PrivateKeyEntry by lazy { load() ?: create() }

    override val certificate: X509Certificate get() = entry.certificate as X509Certificate
    override val privateKey: PrivateKey get() = entry.privateKey

    private fun load(): KeyStore.PrivateKeyEntry? =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.getEntry(alias, null) as? KeyStore.PrivateKeyEntry

    private fun create(): KeyStore.PrivateKeyEntry {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(2048)
            .setCertificateSubject(X500Principal("CN=$CLIENT_NAME"))
            .setCertificateSerialNumber(BigInteger.valueOf(now))
            .setCertificateNotBefore(Date(now - DAY_MS))
            .setCertificateNotAfter(Date(now + VALID_FOR_MS))
            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE, KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .setRandomizedEncryptionRequired(false)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE).apply { initialize(spec) }.generateKeyPair()
        return checkNotNull(load()) { "The keystore lost the key it just made" }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val VALID_FOR_MS = 30 * 365 * DAY_MS
    }
}

internal data class TvPin(val pairing: String, val remote: String? = null) {
    fun admits(fingerprint: String) = remote == null || remote == fingerprint

    fun encode(): String = listOfNotNull(pairing, remote).joinToString(" ")

    companion object {
        fun decode(text: String?): TvPin? {
            val parts = text?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
            return parts.firstOrNull()?.let { TvPin(it, parts.getOrNull(1)) }
        }
    }
}

internal const val CLIENT_NAME = "Tenna RC"
