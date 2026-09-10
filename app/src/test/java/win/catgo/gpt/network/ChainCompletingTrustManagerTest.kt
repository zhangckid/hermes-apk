package win.catgo.gpt.network

import java.io.File
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ChainCompletingTrustManagerTest {
    @Test
    fun `missing issuer is appended before platform validation`() {
        val certificates = File("src/main/res/raw/hermes_ca_intermediates.pem").inputStream().use { input ->
            CertificateFactory.getInstance("X.509")
                .generateCertificates(input)
                .map { it as X509Certificate }
        }
        var validatedChainSize = 0
        val delegate = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                validatedChainSize = chain?.size ?: 0
                if (validatedChainSize < 2) throw CertificateException("issuer missing")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        ChainCompletingTrustManager(delegate, certificates.drop(1))
            .checkServerTrusted(arrayOf(certificates.first()), "ECDSA")

        assertEquals(2, validatedChainSize)
    }
}
