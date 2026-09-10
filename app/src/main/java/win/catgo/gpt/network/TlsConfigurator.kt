package win.catgo.gpt.network

import win.catgo.gpt.i18n.t
import android.content.Context
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import win.catgo.gpt.R

/**
 * Completes certificate paths that Chrome can recover through AIA fetching while retaining the
 * Android platform trust decision. No certificate or hostname checks are disabled.
 */
object TlsConfigurator {
    fun apply(builder: OkHttpClient.Builder, context: Context) {
        val platformTrustManager = platformTrustManager()
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val issuers = context.resources.openRawResource(R.raw.hermes_ca_intermediates).use { input ->
            certificateFactory.generateCertificates(input).map { it as X509Certificate }
        }
        val trustManager = ChainCompletingTrustManager(platformTrustManager, issuers)
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
    }

    private fun platformTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as java.security.KeyStore?)
        }
        return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    }
}

internal class ChainCompletingTrustManager(
    private val delegate: X509TrustManager,
    private val issuerCandidates: List<X509Certificate>,
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val peerChain = chain?.toList().orEmpty()
        if (peerChain.isEmpty()) throw CertificateException(t("服务器没有提供证书"))

        var originalFailure: CertificateException? = null
        candidateChains(peerChain).forEach { candidate ->
            try {
                delegate.checkServerTrusted(candidate.toTypedArray(), authType)
                return
            } catch (failure: CertificateException) {
                if (originalFailure == null) originalFailure = failure
            }
        }
        throw originalFailure ?: CertificateException(t("无法验证服务器证书"))
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    private fun candidateChains(peerChain: List<X509Certificate>): List<List<X509Certificate>> {
        val result = mutableListOf<List<X509Certificate>>()
        val pending = ArrayDeque<List<X509Certificate>>().apply { add(peerChain) }
        while (pending.isNotEmpty()) {
            val path = pending.removeFirst()
            result += path
            if (path.size >= MAX_CHAIN_DEPTH) continue
            val last = path.last()
            issuerCandidates
                .asSequence()
                .filter { it.subjectX500Principal == last.issuerX500Principal }
                .filterNot { candidate -> path.any { it.encoded.contentEquals(candidate.encoded) } }
                .forEach { pending.add(path + it) }
        }
        return result
    }

    private companion object {
        const val MAX_CHAIN_DEPTH = 6
    }
}
