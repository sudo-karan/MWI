package com.ismartcoding.plain.webserver

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Self-signed TLS material for the HTTPS connector (spec §5).
 *
 * A single RSA key + self-signed X.509 cert is persisted in a **BKS** keystore under `filesDir`.
 * The store is written **atomically** (temp file + rename) and **regenerated on corruption** (any
 * load failure ⇒ delete + recreate). The cert's SHA-256 fingerprint is exposed so the UI can show
 * it for trust-on-first-use.
 */
class TlsKeystore(
    private val keystoreFile: File,
    private val password: CharArray,
    private val alias: String = "mwi",
) {
    private val provider: BouncyCastleProvider = BouncyCastleProvider().also {
        if (Security.getProvider(it.name) == null) Security.addProvider(it)
    }

    /** Load the existing keystore, or create+persist a fresh one. Corrupt stores are regenerated. */
    fun loadOrCreate(): KeyStore {
        if (keystoreFile.exists()) {
            runCatching { return loadExisting() }
                .onFailure { keystoreFile.delete() } // corrupt → fall through to regen
        }
        return create()
    }

    private fun loadExisting(): KeyStore {
        val ks = KeyStore.getInstance("BKS", provider.name)
        keystoreFile.inputStream().use { ks.load(it, password) }
        // Sanity: the expected alias must be present with a private key.
        require(ks.isKeyEntry(alias)) { "keystore missing alias $alias" }
        return ks
    }

    private fun create(): KeyStore {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 60 * 60 * 1000)
        val notAfter = Date(now + 3650L * 24 * 60 * 60 * 1000)
        val dn = X500Name("CN=MWI, O=MWI, OU=LAN")
        val certBuilder = JcaX509v3CertificateBuilder(
            dn, BigInteger.valueOf(now), notBefore, notAfter, dn, kp.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(kp.private)
        val cert: X509Certificate = JcaX509CertificateConverter()
            .setProvider(provider).getCertificate(certBuilder.build(signer))

        val ks = KeyStore.getInstance("BKS", provider.name)
        ks.load(null, null)
        ks.setKeyEntry(alias, kp.private, password, arrayOf(cert))

        // Atomic persist: write to a temp sibling, then rename over the target.
        keystoreFile.parentFile?.mkdirs()
        val tmp = File(keystoreFile.parentFile, "${keystoreFile.name}.tmp")
        tmp.outputStream().use { ks.store(it, password) }
        if (!tmp.renameTo(keystoreFile)) {
            tmp.copyTo(keystoreFile, overwrite = true)
            tmp.delete()
        }
        return ks
    }

    /** SHA-256 fingerprint of the leaf certificate, as colon-separated uppercase hex (TOFU). */
    fun certificateFingerprint(ks: KeyStore): String {
        val cert = ks.getCertificate(alias) as X509Certificate
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
    }
}
