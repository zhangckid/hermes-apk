package win.catgo.gpt.network

import okhttp3.OkHttpClient
import java.io.IOException
import win.catgo.gpt.model.ServerConfig

/** Authentication bodies must not be forwarded by HTTP redirects to another origin. */
internal object ServerHttpPolicy {
    /** Restrict authenticated transport to the exact origin explicitly selected by the user. */
    fun restrictToServer(builder: OkHttpClient.Builder, config: ServerConfig): OkHttpClient.Builder =
        builder.addInterceptor { chain ->
            val url = chain.request().url
            if (url.scheme != config.scheme || url.host != config.normalizedHost || url.port != config.port) {
                throw IOException("Request does not match the configured server origin")
            }
            chain.proceed(chain.request())
        }

    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
        .followRedirects(false)
        .followSslRedirects(false)
}
