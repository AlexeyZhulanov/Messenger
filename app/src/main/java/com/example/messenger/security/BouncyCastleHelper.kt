package com.example.messenger.security

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

class BouncyCastleHelper {

    fun createSelfSignedCertificate(keyPair: KeyPair): X509Certificate {
        Security.addProvider(BouncyCastleProvider())

        val issuer = X500Name("CN=Self-Signed Certificate")
        val serialNumber = BigInteger.valueOf(System.currentTimeMillis())
        val startDate = Date()
        val endDate = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000 * 10) // 10 years

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer,
            serialNumber,
            startDate,
            endDate,
            issuer,
            keyPair.public
        )

        val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .build(keyPair.private)

        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider())
            .getCertificate(certBuilder.build(signer))
    }
}