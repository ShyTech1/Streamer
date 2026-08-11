package com.streamer.app.http

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.util.Date

object SelfSignedCert {
    fun make(
        kp: KeyPair,
        subject: String,
        serial: BigInteger,
        notBefore: Date,
        notAfter: Date,
    ): X509Certificate {
        val name = X500Name(subject)
        val builder = JcaX509v3CertificateBuilder(name, serial, notBefore, notAfter, name, kp.public)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }
}
