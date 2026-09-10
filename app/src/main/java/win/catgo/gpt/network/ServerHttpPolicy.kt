package win.catgo.gpt.network

import okhttp3.OkHttpClient

/** Authentication bodies must not be forwarded by HTTP redirects to another origin. */
internal object ServerHttpPolicy {
    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
        .followRedirects(false)
        .followSslRedirects(false)
}
