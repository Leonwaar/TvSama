package fr.nekotv

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.Normalizer
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal const val VIDRIFT_ORIGIN = "https://embed.vidrift.net"
internal const val VIDRIFT_MIME_TYPE = "application/x-tvsama-vidrift"
internal const val VIDRIFT_PARENT = "https://appassets.androidplatform.net"
private const val VIDRIFT_SITE = "https://vidrift.net/"

private val vidriftAudioPatterns = mapOf(
    "fr" to Regex("(?i)\\b(fr|fra|fre|french|français|francais|vf|vff|vfq|truefrench)\\b"),
    "en" to Regex("(?i)\\b(en|eng|english|anglais)\\b"),
    "ja" to Regex("(?i)\\b(ja|jpn|japanese|japonais)\\b"),
    "ko" to Regex("(?i)\\b(ko|kor|korean|coréen|coreen)\\b"),
    "zh" to Regex("(?i)\\b(zh|zho|chi|chinese|chinois|mandarin|cantonese)\\b"),
    "es" to Regex("(?i)\\b(es|spa|spanish|espagnol|espanol|castellano|latino)\\b"),
    "de" to Regex("(?i)\\b(de|deu|ger|german|deutsch|allemand)\\b"),
    "it" to Regex("(?i)\\b(it|ita|italian|italiano|italien)\\b"),
    "pt" to Regex("(?i)\\b(pt|por|portuguese|portugais)\\b"),
    "hi" to Regex("(?i)\\b(hi|hin|hindi)\\b"),
    "ta" to Regex("(?i)\\b(ta|tam|tamil|tamoul)\\b"),
    "te" to Regex("(?i)\\b(te|tel|telugu|telougou)\\b"),
    "ru" to Regex("(?i)\\b(ru|rus|russian|russe)\\b"),
    "ar" to Regex("(?i)\\b(ar|ara|arabic|arabe)\\b")
)
private val vidriftSubtitleMarker = Regex("(?i)\\b(vostfr|sub|subs|subtitle|subtitles|sous[- ]?titres?)\\b")
private val vidriftFrenchSubtitle = Regex("(?i)\\b(fr|fra|fre|french|français|francais|vostfr)\\b")

/** The sole audio language a label proves, or null when it is absent or ambiguous. */
internal fun vidriftAudioLanguage(label: String): String? {
    val text = Normalizer.normalize(label.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    if (vidriftSubtitleMarker.containsMatchIn(text)) return null
    return vidriftAudioPatterns.filterValues { it.containsMatchIn(text) }.keys.singleOrNull()
}

internal fun vidriftIsFrenchSubtitle(label: String): Boolean =
    vidriftFrenchSubtitle.containsMatchIn(Normalizer.normalize(label.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]+"), " "))

/** VF needs confirmed French audio; VOSTFR needs the declared original audio and French subtitles. */
internal fun vidriftAcceptsAudio(requested: String, original: String?, label: String): Boolean {
    val audio = vidriftAudioLanguage(label) ?: return false
    return when (requested) {
        "VF" -> audio == "fr"
        "VOSTFR" -> original?.takeIf { it.matches(Regex("[a-z]{2}")) }?.let { it != "fr" && audio == it } == true
        else -> false
    }
}


internal fun vidriftOriginFor(siteAddress: String): String {
    val uri = runCatching { URI(siteAddress) }.getOrNull() ?: return VIDRIFT_ORIGIN
    if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) return VIDRIFT_ORIGIN
    return when (uri.host?.lowercase()) {
        "vidrift.net" -> VIDRIFT_ORIGIN
        "vidrift.in" -> "https://embed.vidrift.in"
        else -> VIDRIFT_ORIGIN
    }
}

internal fun currentVidriftOrigin() = vidriftOriginFor(
    com.streamflixreborn.streamflix.utils.SourceAddresses.current("Vidrift", VIDRIFT_SITE))

/** Vidrift publishes an iframe API, not a native video URL.
 *  Both VF and VOSTFR are offered simultaneously when available:
 *  - VF is always offered (Vidrift carries French dubs for most content).
 *  - VOSTFR is offered when the original language is known and non-French.
 *  The requested [language] is listed first so the resolver picks it as default. */
internal fun vidriftSources(anime: Anime, episode: Episode?, language: String): List<VideoSource> {
    if (anime.isLiveContent() || anime.tag !in listOf("Film", "Série") || language !in listOf("VF", "VOSTFR")) return emptyList()
    val movie = anime.tag == "Film"
    val refs = (anime.references + MediaReference(anime.provider, anime.id, anime.tag)).filter { it.provider == "WarFlix" && it.tag == anime.tag }
    val ids = (listOfNotNull(anime.tmdbId) + refs.mapNotNull {
        val path = runCatching { URI(it.id).path }.getOrNull().orEmpty()
        Regex(if (movie) "^/film/([0-9]+)/?$" else "^/series/([0-9]+)/?$").matchEntire(path)?.groupValues?.get(1)?.toIntOrNull()
    }).filter { it > 0 }.distinct()
    val id = ids.singleOrNull() ?: return emptyList()
    val original = anime.originalLanguage ?: refs.firstNotNullOfOrNull {
        com.streamflixreborn.streamflix.providers.WarFlixProvider.originalLanguage(it.id)
    }
    val season = episode?.introSeason ?: episode?.seasonNumber
    val number = episode?.introNumber ?: episode?.number
    if (!movie && (season == null || season < 0 || number == null || number < 1)) return emptyList()
    val route = if (movie) "movie/$id" else "tv/$id/$season/$number"
    val hasVostfr = original?.matches(Regex("[a-z]{2}")) == true && original != "fr"
    val modes = when (language) {
        "VOSTFR" -> if (hasVostfr) listOf("VOSTFR") else emptyList()
        "VF" -> listOf("VF")
        else -> emptyList()
    }
    return modes.map { mode ->
        val url = Uri.parse("${currentVidriftOrigin()}/embed/$route").buildUpon()
            .appendQueryParameter("brand", "TvSama").appendQueryParameter("brandColor", "C8F36B")
            .appendQueryParameter("autoplay", "0").appendQueryParameter("exit", "1")
            .appendQueryParameter("showTitle", "1").appendQueryParameter("fs", "parent")
            .fragment(mode).build().toString()
        VideoSource(if (mode == "VF") "VF" else "VOSTFR", url, mode, "Auto", "Vidrift",
            mimeType = VIDRIFT_MIME_TYPE, reachable = false, serverId = original.orEmpty(),
            originalLanguage = original?.takeIf { it.isNotBlank() })
    }
}

/** Returns VF and/or VOSTFR AniList sources based on origin language availability.
 *  When both are available the requested [language] is listed first. */
internal fun vidriftAniListSources(anime: Anime, episode: Episode, title: AniListTitle, language: String): List<VideoSource> {
    if (anime.tag != "Série" || language !in listOf("VF", "VOSTFR") || title.id <= 0) return emptyList()
    val number = vidriftAniListEpisode(anime, episode) ?: return emptyList()
    if (number < 1 || title.episodes != null && number > title.episodes) return emptyList()
    val original = languageForAniListCountry(title.country)
    val hasVostfr = original != null && original != "fr"
    val modes = when {
        language == "VOSTFR" && hasVostfr -> listOf("VOSTFR")
        language == "VF" -> listOf("VF")
        // An unknown original language cannot prove that VOSTFR is available.
        else -> emptyList()
    }
    if (modes.isEmpty()) return emptyList()
    return modes.map { mode ->
        val url = Uri.parse("${currentVidriftOrigin()}/embed/anilist/${title.id}/$number").buildUpon()
            .appendQueryParameter("brand", "TvSama").appendQueryParameter("brandColor", "C8F36B")
            .appendQueryParameter("autoplay", "0").appendQueryParameter("exit", "1")
            .appendQueryParameter("showTitle", "1").appendQueryParameter("fs", "parent")
            .fragment(mode).build().toString()
        VideoSource(if (mode == "VF") "VF" else "VOSTFR", url, mode, "Auto", "Vidrift",
            mimeType = VIDRIFT_MIME_TYPE, reachable = false, serverId = original.orEmpty(),
            originalLanguage = original?.takeIf { it.isNotBlank() })
    }
}

/** Compat shim kept for call sites still using the single-source signature. */
internal fun vidriftAniListSource(anime: Anime, episode: Episode, title: AniListTitle, language: String): VideoSource? =
    vidriftAniListSources(anime, episode, title, language).firstOrNull()

internal fun vidriftConfig(source: VideoSource, resumeAt: Long, television: Boolean): JSONObject? {
    if (source.provider != "Vidrift" || source.mimeType != VIDRIFT_MIME_TYPE || source.language !in listOf("VF", "VOSTFR")) return null
    val uri = runCatching { URI(source.url) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host !in setOf("embed.vidrift.net", "embed.vidrift.in") || uri.userInfo != null || uri.port != -1) return null
    val movieMatch = Regex("^/embed/movie/([1-9][0-9]*)$").matchEntire(uri.path)
    val tvMatch = Regex("^/embed/tv/([1-9][0-9]*)/([0-9]+)/([1-9][0-9]*)$").matchEntire(uri.path)
    val animeMatch = Regex("^/embed/anilist/([1-9][0-9]*)/([1-9][0-9]*)$").matchEntire(uri.path)
    val kind = when { movieMatch != null -> "movie"; tvMatch != null -> "tv"; animeMatch != null -> "anilist"; else -> return null }
    val id = movieMatch?.groupValues?.get(1) ?: tvMatch?.groupValues?.get(1) ?: animeMatch!!.groupValues[1]
    return JSONObject().put("origin", "${uri.scheme}://${uri.host}").put("tmdbId", id)
        .put("mediaType", kind).put("aniListId", if (kind == "anilist") id else "")
        .put("season", tvMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0)
        .put("episode", tvMatch?.groupValues?.get(3)?.toIntOrNull() ?: animeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0)
        .put("language", source.language).put("originalLanguage", (source.originalLanguage ?: source.serverId.takeIf { it.matches(Regex("[a-z]{2}")) }).orEmpty())
        .put("resumeAt", resumeAt.coerceAtLeast(0) / 1000.0)
        .put("url", Uri.parse(source.url).buildUpon().appendQueryParameter("uiScale", if (television) "1.5" else "1").build().toString())
}

/** The official Vidrift integration is a cross-origin iframe, so a direct API call can be
 *  refused. Load that iframe hidden, drive its public postMessage API, confirm the requested
 *  audio (and French subtitles for VOSTFR), then capture the HLS manifest it requests. The
 *  result then plays in TvSama's native ExoPlayer controls. */
@SuppressLint("SetJavaScriptEnabled")
internal suspend fun resolveVidriftWebStream(context: Context, source: VideoSource): VideoSource? {
    if (source.provider != "Vidrift" || source.mimeType != VIDRIFT_MIME_TYPE) return null
    val requested = source.language
    if (requested !in listOf("VF", "VOSTFR")) return null
    val original = (source.originalLanguage ?: source.serverId.takeIf { it.matches(Regex("[a-z]{2}")) }).orEmpty()
    if (requested == "VOSTFR" && (original.isBlank() || original == "fr")) return null
    val uri = runCatching { URI(source.url) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host !in setOf("embed.vidrift.net", "embed.vidrift.in") ||
        uri.userInfo != null || uri.port !in listOf(-1, 443)) return null
    val origin = "${uri.scheme}://${uri.host}"
    val embedUri = Uri.parse(source.url)
    val embed = embedUri.buildUpon().clearQuery().apply {
        embedUri.queryParameterNames.filter { it != "autoplay" }.forEach { name ->
            embedUri.getQueryParameters(name).forEach { appendQueryParameter(name, it.orEmpty()) }
        }
        appendQueryParameter("muted", "1")
    }.build().toString()
    // Port of Vidrift's documented parent -> player API. The parent confirms the exact audio
    // language before playback is approved; anything unknown or another dub keeps cycling.
    val bootstrap = """
        (function(){
          var origin='$origin';
          var requested='$requested';
          var original='$original';
          var player=document.getElementById('player');
          var state=null,approved=false,failed=false,pending=null,started=Date.now();
          var tried=new Set(),choices=new Set();
          function norm(v){return String(v||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();}
          function audioLanguage(label){
            var text=norm(label);
            if(/\b(vostfr|sub|subs|subtitle|subtitles|sous[- ]?titres?)\b/.test(text))return null;
            var patterns={fr:/\b(fr|fra|fre|french|francais|vf|vff|vfq|truefrench)\b/,en:/\b(en|eng|english|anglais)\b/,ja:/\b(ja|jpn|japanese|japonais)\b/,ko:/\b(ko|kor|korean|coreen)\b/,zh:/\b(zh|zho|chi|chinese|chinois|mandarin|cantonese)\b/,es:/\b(es|spa|spanish|espagnol|espanol|castellano|latino)\b/,de:/\b(de|deu|ger|german|deutsch|allemand)\b/,it:/\b(it|ita|italian|italiano|italien)\b/,pt:/\b(pt|por|portuguese|portugais)\b/,hi:/\b(hi|hin|hindi)\b/,ta:/\b(ta|tam|tamil|tamoul)\b/,te:/\b(te|tel|telugu|telougou)\b/,ru:/\b(ru|rus|russian|russe)\b/,ar:/\b(ar|ara|arabic|arabe)\b/};
            var found=Object.keys(patterns).filter(function(k){return patterns[k].test(text);});
            return found.length===1?found[0]:null;
          }
          function frenchSub(label){
            var text=norm(label);
            return /\b(fr|fra|fre|french|francais|vostfr)\b/.test(text)&&!/\b(off|disabled|none)\b/.test(text);
          }
          function allowed(label){
            var audio=audioLanguage(label);
            if(requested==='VF')return audio==='fr';
            if(original)return audio===original;
            return audio!==null&&audio!=='fr';
          }
          function options(v){return Array.isArray(v)?v.filter(function(o){return o&&typeof o.value==='string'&&typeof o.label==='string';}).slice(0,80):[];}
          function send(msg){try{player.contentWindow.postMessage(msg,origin);}catch(e){}}
          function stop(){if(failed)return;failed=true;approved=false;window.__tvsamaVidriftStatus='failed';send({type:'vidrift:pause'});}
          function select(field,value){
            pending={field:field,value:value,at:Date.now(),loading:false};
            if(field==='server')tried.add(value);
            choices.add([state&&state.server,field,value].join('|'));
            approved=false;window.__tvsamaVidriftStatus='pending';
            send({type:'vidrift:pause'});
            var msg={type:'vidrift:set'};msg[field]=value;send(msg);
          }
          function nextServer(){
            if(!state)return;
            var next=options(state.servers).find(function(s){return s.state!=='failed'&&!tried.has(s.value);});
            if(next&&tried.size<6)select('server',next.value);else stop();
          }
          function receive(d){
            if(!d||typeof d.type!=='string')return;
            if(failed){if(d.type==='vidrift:state'&&d.paused===false)send({type:'vidrift:pause'});return;}
            if(d.type!=='vidrift:state')return;
            state=d;
            if(pending){
              if(d[pending.field]!==pending.value)return;
              if(Number(d.duration)===0)pending.loading=true;
              if(pending.field==='server'&&!pending.loading&&Number(d.duration)>0)return;
              if(!(Number(d.duration)>0))return;
              pending=null;
            }
            var duration=Number(d.duration);
            if(!isFinite(duration)||duration<=0)return;
            if(duration<90){tried.add(d.server);nextServer();return;}
            tried.add(d.server);
            var audios=options(d.audios),sources=options(d.sources),subtitles=options(d.subtitles);
            var current=audios.length?audios.find(function(a){return a.value===d.audio;}):sources.find(function(s){return s.value===d.source;});
            var frSub=subtitles.find(function(s){return s.value!=='off'&&frenchSub(s.label);});
            var audioReady=current&&allowed(current.label);
            var subtitleReady=requested!=='VOSTFR'||(frSub&&d.subtitle===frSub.value);
            if(audioReady&&subtitleReady){
              if(!approved){
                approved=true;window.__tvsamaVidriftStatus='ready';
                if(frSub)send({type:'vidrift:set',subtitle:frSub.value});
                send({type:'vidrift:set',muted:true});
                send({type:'vidrift:play'});
              }
              return;
            }
            approved=false;window.__tvsamaVidriftStatus='pending';send({type:'vidrift:pause'});
            var audio=audios.find(function(a){return a.value!==d.audio&&allowed(a.label)&&!choices.has([d.server,'audio',a.value].join('|'));});
            var source=sources.find(function(s){return s.value!==d.source&&allowed(s.label)&&!choices.has([d.server,'source',s.value].join('|'));});
            if(audio)select('audio',audio.value);
            else if(source)select('source',source.value);
            else if(audioReady&&requested==='VOSTFR'&&frSub&&!choices.has([d.server,'subtitle',frSub.value].join('|')))select('subtitle',frSub.value);
            else nextServer();
          }
          window.__tvsamaVidriftStatus='pending';
          window.addEventListener('message',function(e){if(e.origin===origin){try{receive(e.data);}catch(err){}}});
          player.addEventListener('load',function(){
            window.__tvsamaVidriftStatus='pending';approved=false;state=null;tried.clear();choices.clear();
            send({type:'vidrift:play'});send({type:'vidrift:set',muted:true});
          });
          var kick=setInterval(function(){
            if(failed){clearInterval(kick);return;}
            if(state){clearInterval(kick);return;}
            send({type:'vidrift:play'});send({type:'vidrift:set',muted:true});
            if(Date.now()-started>12000)clearInterval(kick);
          },700);
          setInterval(function(){
            if(failed){return;}
            if(pending&&Date.now()-pending.at>5000){
              // A server that never reports a duration is stalled: move on instead of
              // waiting for the global budget.
              var field=pending.field,value=pending.value;
              pending=null;
              if(field==='server'){tried.add(value);nextServer();}
              else nextServer();
              return;
            }
            if(!approved&&Date.now()-started>17000)stop();
          },1000);
        })();
    """.trimIndent()
    val captured = try {
        withTimeoutOrNull(20_000) {
            resolveLiveWebPlayer(context, embed, "$origin/", allowProgressive = false,
                iframeBootstrap = bootstrap, captureGate = "window.__tvsamaVidriftStatus",
                captureFailureValue = "failed", captureSubtitles = requested == "VOSTFR")
        }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e
    } catch (_: Exception) { null } ?: return null
    if (requested == "VOSTFR") {
        // The web player confirms the French subtitle track exists. Native playback needs it
        // as a sidecar URL or inside the manifest; otherwise VOSTFR would play as plain VO.
        val frenchSubtitles = captured.subtitles.filter {
            it.language.lowercase() in listOf("fr", "fra", "fre") || vidriftIsFrenchSubtitle(it.label)
        }.map { it.copy(language = "fr") }
        if (frenchSubtitles.isEmpty() && !hlsDeclaresFrenchSubtitles(captured)) return null
        return captured.copy(
            name = "Vidrift · VOSTFR",
            provider = "Vidrift",
            language = requested,
            quality = "Auto",
            subtitles = frenchSubtitles,
            serverId = source.serverId,
            originalLanguage = original.takeIf { it.isNotBlank() },
            reachable = true
        )
    }
    return captured.copy(
        name = "Vidrift · VF",
        provider = "Vidrift",
        language = requested,
        quality = "Auto",
        subtitles = emptyList(),
        serverId = source.serverId,
        originalLanguage = original.takeIf { it.isNotBlank() },
        reachable = true
    )
}

/**
 * Resolves native HLS media streams from Vidrift API so they can be played
 * directly in TvSama's native player (ExoPlayer).
 */
internal suspend fun resolveVidriftStream(source: VideoSource): VideoSource? = withContext(Dispatchers.IO) {
    if (source.provider != "Vidrift") return@withContext null
    val requested = source.language
    if (requested !in listOf("VF", "VOSTFR")) return@withContext null
    val original = source.originalLanguage ?: source.serverId.takeIf { it.matches(Regex("[a-z]{2}")) }
    if (requested == "VOSTFR" && (original == null || original == "fr")) return@withContext null
    val uri = runCatching { URI(source.url) }.getOrNull() ?: return@withContext null
    val origin = vidriftOriginFor("${uri.scheme}://${uri.host}")
    var route = uri.path.substringAfter("/embed/").trim('/')
    if (route.isBlank()) return@withContext null

    fun readSubtitles(array: JSONArray?): List<VideoSubtitle> {
        if (array == null) return emptyList()
        return List(array.length()) { index -> array.optJSONObject(index) }.filterNotNull().mapNotNull { track ->
            val lang = track.optString("lang").ifBlank { track.optString("language") }
            val label = track.optString("label").ifBlank { track.optString("name") }
            val url = track.optString("url").ifBlank { track.optString("file") }
            if (url.isBlank()) null else VideoSubtitle(
                if (url.startsWith("http")) url else "$origin$url",
                lang.ifBlank { "fr" }, label.ifBlank { "Français" })
        }
    }
    fun frenchOnly(tracks: List<VideoSubtitle>) = tracks.filter {
        it.language.lowercase() in listOf("fr", "fra", "fre") || vidriftIsFrenchSubtitle(it.label)
    }

    fun orionSubtitles(listUrl: String): List<VideoSubtitle> {
        if (listUrl.isBlank()) return emptyList()
        return try {
            val address = if (listUrl.startsWith("http")) listUrl else "$origin$listUrl"
            val request = Request.Builder().url(address)
                .header("User-Agent", com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider.USER_AGENT)
                .header("x-embed-parent", origin)
                .header("Referer", "$origin/embed/$route")
                .build()
            MediaNetwork.client.newCall(request).execute().use { response ->
                val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                readSubtitles(json?.optJSONArray("tracks"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) { emptyList() }
    }

    val client = MediaNetwork.client
    if (route.startsWith("anilist/")) {
        try {
            val req = Request.Builder()
                .url("$origin/embed/$route")
                .header("User-Agent", com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider.USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                val finalPath = resp.request.url.encodedPath
                if (finalPath.contains("/embed/")) {
                    route = finalPath.substringAfter("/embed/").trim('/')
                }
            }
        } catch (_: Exception) {}
    }

    try {
        val bootUrl = "$origin/api/boot/$route"
        val bootReq = Request.Builder()
            .url(bootUrl)
            .header("User-Agent", com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider.USER_AGENT)
            .header("x-embed-parent", origin)
            .header("Referer", "$origin/embed/$route")
            .build()
        val (meta, cookie) = client.newCall(bootReq).execute().use { resp ->
            val setCookie = resp.header("Set-Cookie").orEmpty()
            val body = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            json?.optJSONObject("meta") to setCookie
        }
        if (meta == null) return@withContext null

        val orionStreams = meta.optJSONArray("orionStreams")
        val playbackToken = meta.optString("playbackToken")

        // 1. Orion streams expose a readable audio label, so only a confirmed match is used.
        if (orionStreams != null && orionStreams.length() > 0) {
            for (i in 0 until orionStreams.length()) {
                val stream = orionStreams.optJSONObject(i) ?: continue
                if (!vidriftAcceptsAudio(requested, original, stream.optString("name"))) continue
                val relative = stream.optString("url")
                val address = if (relative.startsWith("http")) relative else "$origin$relative"
                if (address.isBlank() || address == origin) continue
                val tracks = orionSubtitles(stream.optString("subtitleListUrl"))
                val subtitles = if (requested == "VOSTFR") frenchOnly(tracks) else tracks
                if (requested == "VOSTFR" && subtitles.isEmpty()) continue
                val rungs = stream.optJSONArray("rungs")
                val quality = if (rungs != null && rungs.length() > 0) "${rungs.optJSONObject(0)?.optInt("height", 1080)}p" else "1080p"
                return@withContext VideoSource(
                    name = "Vidrift · $requested",
                    url = address,
                    language = requested,
                    quality = quality,
                    provider = "Vidrift",
                    headers = mapOf("Referer" to "$origin/"),
                    subtitles = subtitles,
                    mimeType = "application/x-mpegURL",
                    reachable = true,
                    latencyMs = 0,
                    originalLanguage = original
                )
            }
        }

        // 2. The /api/source fallback must declare the audio language itself. An unlabeled
        //    stream is rejected instead of being relabelled as the requested version.
        if (playbackToken.isNotBlank()) {
            val srcUrl = "$origin/api/source/$route?token=${Uri.encode(playbackToken)}"
            val srcReq = Request.Builder().url(srcUrl)
                .header("User-Agent", com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider.USER_AGENT)
                .header("x-embed-parent", origin)
                .header("Referer", "$origin/embed/$route")
                .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                .build()
            client.newCall(srcReq).execute().use { srcResp ->
                val srcBody = srcResp.body?.string().orEmpty()
                val srcJson = runCatching { JSONObject(srcBody) }.getOrNull()
                val streams = srcJson?.optJSONArray("streams")
                val globalLanguage = srcJson?.optString("language").orEmpty()
                if (streams != null) for (i in 0 until streams.length()) {
                    val stream = streams.optJSONObject(i) ?: continue
                    val label = listOf("language", "lang", "audio", "label", "name", "title")
                        .map { stream.optString(it) }.filter { it.isNotBlank() }.joinToString(" ")
                        .ifBlank { globalLanguage }
                    if (!vidriftAcceptsAudio(requested, original, label)) continue
                    val proxyUrl = stream.optString("proxyUrl")
                    val normalUrl = stream.optString("url")
                    val finalUrl = proxyUrl.ifEmpty { normalUrl }
                    if (finalUrl.isBlank()) continue
                    val tracks = readSubtitles(stream.optJSONArray("subtitles"))
                        .ifEmpty { readSubtitles(srcJson?.optJSONArray("subtitles")) }
                        .ifEmpty { readSubtitles(stream.optJSONArray("tracks")) }
                    val subtitles = if (requested == "VOSTFR") frenchOnly(tracks) else tracks
                    if (requested == "VOSTFR" && subtitles.isEmpty()) continue
                    return@withContext VideoSource(
                        name = "Vidrift · $requested",
                        url = finalUrl,
                        language = requested,
                        quality = srcJson?.optString("quality").orEmpty().ifBlank { "HD" },
                        provider = "Vidrift",
                        headers = mapOf("Referer" to "$origin/"),
                        subtitles = subtitles,
                        mimeType = "application/x-mpegURL",
                        reachable = true,
                        latencyMs = 0
                    )
                }
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e
    } catch (_: Exception) {}
    null
}

internal suspend fun resolveVidriftNativeSources(anime: Anime, episode: Episode?, language: String, aniListTitle: AniListTitle? = null): List<VideoSource> = withContext(Dispatchers.IO) {
    if (anime.isLiveContent() || anime.tag !in listOf("Film", "Série") || language !in listOf("VF", "VOSTFR")) return@withContext emptyList()
    val baseSources = if (aniListTitle != null && episode != null) {
        vidriftAniListSources(anime, episode, aniListTitle, language)
    } else {
        vidriftSources(anime, episode, language)
    }
    // Do not resolve the stream while the source list is being built: the API/hidden
    // capture can take several seconds per candidate. Playback resolves only the chosen
    // Vidrift version, and caches it for retries.
    baseSources
}

