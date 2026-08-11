package com.streamer.app.http

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory

object Tls {
    private const val KS_FILE = "streamer.p12"
    private const val KS_PASS = "streamer"

    fun serverSocketFactory(ctx: Context): SSLServerSocketFactory {
        val ksFile = File(ctx.filesDir, KS_FILE)
        if (!ksFile.exists()) generateKeystore(ksFile)

        val ks = KeyStore.getInstance("PKCS12")
        FileInputStream(ksFile).use { ks.load(it, KS_PASS.toCharArray()) }

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, KS_PASS.toCharArray())

        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(kmf.keyManagers, null, SecureRandom())
        return sslCtx.serverSocketFactory
    }

    private fun generateKeystore(out: File) {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val cert = SelfSignedCert.make(
            kp,
            subject = "CN=streamer",
            serial = BigInteger.valueOf(System.currentTimeMillis()),
            notBefore = Date(),
            notAfter = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000 * 10)
        )

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("streamer", kp.private, KS_PASS.toCharArray(), arrayOf(cert))
        FileOutputStream(out).use { ks.store(it, KS_PASS.toCharArray()) }
    }
}
