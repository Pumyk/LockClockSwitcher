package com.example.lockclock

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

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
                initialize(2048, SecureRandom())
            }.generateKeyPair()

            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + 10L * 365 * 24 * 3600 * 1000)
            val name = X500Name("CN=LockClockSwitcher")
            val builder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(System.currentTimeMillis()), notBefore, notAfter, name, kp.public
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
            val x509 = JcaX509CertificateConverter().getCertificate(builder.build(signer))

            k = kp.private
            c = x509
            keyFile.writeBytes(kp.private.encoded)
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
