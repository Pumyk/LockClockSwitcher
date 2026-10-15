package com.example.lockclock

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import sun.security.x509.AlgorithmId
import sun.security.x509.CertificateAlgorithmId
import sun.security.x509.CertificateExtensions
import sun.security.x509.CertificateIssuerName
import sun.security.x509.CertificateSerialNumber
import sun.security.x509.CertificateSubjectName
import sun.security.x509.CertificateValidity
import sun.security.x509.CertificateVersion
import sun.security.x509.CertificateX509Key
import sun.security.x509.KeyIdentifier
import sun.security.x509.PrivateKeyUsageExtension
import sun.security.x509.SubjectKeyIdentifierExtension
import sun.security.x509.X500Name
import sun.security.x509.X509CertImpl
import sun.security.x509.X509CertInfo
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random

/**
 * Holds this app's ADB identity. The RSA key + certificate are generated once and kept in
 * the app's private storage, so after pairing once you don't need a new code each time.
 */
class AdbManager private constructor(ctx: Context) : AbsAdbConnectionManager() {

    private val key: PrivateKey
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val keyFile = File(ctx.filesDir, "adb_key.pk8")
        val certFile = File(ctx.filesDir, "adb_cert.der")

        var k: PrivateKey? = null
        var c: Certificate? = null
        if (keyFile.exists() && certFile.exists()) {
            runCatching {
                k = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                certFile.inputStream().use { c = CertificateFactory.getInstance("X.509").generateCertificate(it) }
            }
        }
        if (k == null || c == null) {
            val kp = KeyPairGenerator.getInstance("RSA").apply {
                initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
            }.generateKeyPair()
            val pub = kp.public
            val priv = kp.private

            val algorithm = "SHA512withRSA"
            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + 10L * 365 * 24 * 3600 * 1000)
            val name = X500Name("CN=LockClockSwitcher")

            val ext = CertificateExtensions()
            ext.set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(pub).identifier))
            ext.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))

            val info = X509CertInfo()
            info.set("version", CertificateVersion(2))
            info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
            info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithm)))
            info.set("subject", CertificateSubjectName(name))
            info.set("key", CertificateX509Key(pub))
            info.set("validity", CertificateValidity(notBefore, notAfter))
            info.set("issuer", CertificateIssuerName(name))
            info.set("extensions", ext)
            val x509 = X509CertImpl(info)
            x509.sign(priv, algorithm)

            k = priv
            c = x509
            keyFile.writeBytes(priv.encoded)
            certFile.writeBytes(x509.encoded)
        }
        key = k!!
        cert = c!!
    }

    override fun getPrivateKey(): PrivateKey = key
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "LockClockSwitcher"

    companion object {
        @Volatile private var instance: AdbManager? = null
        fun get(ctx: Context): AdbManager =
            instance ?: synchronized(this) {
                instance ?: AdbManager(ctx.applicationContext).also { instance = it }
            }
    }
}
