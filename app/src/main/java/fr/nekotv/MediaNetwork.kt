package fr.nekotv

import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.NetworkClient
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.TimeUnit

/** Extraction, probes and playback use the same DNS and persisted browser cookies. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object MediaNetwork {
    val client: OkHttpClient by lazy { OkHttpClient.Builder().dns(DnsResolver.doh)
        .cookieJar(NetworkClient.cookieJar).connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
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
    val probeClient: OkHttpClient by lazy { client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build() }
    fun factory(source: VideoSource): OkHttpDataSource.Factory {
        val host = java.net.URI(source.url).host
        val routed = client.newBuilder().addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(if (request.url.host == host) request else request.newBuilder().removeHeader("Cookie").removeHeader("Authorization").build())
        }.build()
        val userAgent = source.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: TvSamaAnimeProvider.USER_AGENT
        return OkHttpDataSource.Factory(routed).setDefaultRequestProperties(source.headers).setUserAgent(userAgent)
    }
}
