package fr.nekotv

import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.NetworkClient
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.TimeUnit

/** Extraction, probes and playback use the same DNS and persisted browser cookies. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object MediaNetwork {
    private fun credentials(request: okhttp3.Request, origin: okhttp3.HttpUrl): okhttp3.Request {
        if (request.url.host == origin.host && request.url.port == origin.port && request.url.scheme == origin.scheme) return request
        return request.newBuilder().removeHeader("Cookie").removeHeader("Authorization").apply {
            // Only the destination's own cookie jar may restore a session after a redirect.
            val cookies = NetworkClient.cookieJar.loadForRequest(request.url)
            if (cookies.isNotEmpty()) header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
        }.build()
    }
    val client: OkHttpClient by lazy { OkHttpClient.Builder().dns(DnsResolver.doh)
        .cookieJar(NetworkClient.cookieJar).connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(com.streamflixreborn.streamflix.utils.SourceAddresses.interceptor)
        .addNetworkInterceptor { chain ->
            val origin = chain.request().tag(okhttp3.HttpUrl::class.java) ?: chain.call().request().url
            val request = chain.request()
            chain.proceed(credentials(request, origin))
        }
        .addInterceptor { chain ->
            // Record the routed origin before OkHttp follows redirects.
            val request = chain.request().newBuilder().tag(okhttp3.HttpUrl::class.java, chain.request().url).build()
            val path = request.url.encodedPath.lowercase()
            val imageEnvelope = path.endsWith(".image") || path.endsWith(".png") || path.endsWith(".webp") || path.endsWith(".jpg")
            val actualRequest = if (imageEnvelope) request.newBuilder().removeHeader("Range").build() else request
            var response = chain.proceed(actualRequest)
            if (response.code == 206 && WrappedTs.isWrapped(response.peekBody(12).bytes())) {
                response.close()
                response = chain.proceed(actualRequest.newBuilder().removeHeader("Range").build())
            }
            val body = response.body
            if (!response.isSuccessful || body == null || !WrappedTs.isWrapped(response.peekBody(12).bytes())) response
            else {
                val decoded = body.byteStream().use { WrappedTs.decode(WrappedTs.readBounded(it)) }
                    ?: throw java.io.IOException("Segment vidéo non reconnu")
                response.newBuilder().code(200).removeHeader("Content-Range").removeHeader("Content-Encoding")
                    .header("Content-Length", decoded.size.toString()).header("Content-Type", "video/mp2t")
                    .body(decoded.toResponseBody("video/mp2t".toMediaType())).build()
            }
        }.build() }
    val probeClient: OkHttpClient by lazy { client.newBuilder().callTimeout(4, TimeUnit.SECONDS).build() }
    fun factory(source: VideoSource): OkHttpDataSource.Factory {
        val origin = com.streamflixreborn.streamflix.utils.SourceAddresses.rewrite(source.url).toHttpUrl()
        val routed = client.newBuilder().addNetworkInterceptor { chain ->
            val request = chain.request()
            chain.proceed(credentials(request, origin))
        }.build()
        val userAgent = source.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: TvSamaAnimeProvider.USER_AGENT
        return OkHttpDataSource.Factory(routed).setDefaultRequestProperties(source.headers).setUserAgent(userAgent)
    }
}
