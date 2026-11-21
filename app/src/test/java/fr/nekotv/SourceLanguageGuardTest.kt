package fr.nekotv

import org.junit.Assert.*
import org.junit.Test

/** A server label is never enough: the declared renditions must match the chosen version. */
class SourceLanguageGuardTest {
    @Test fun declaredCatalanIsNotFrenchAudio() {
        assertEquals("VOSTFR", mediaLanguage("Lecteur VOST", emptyList()))
        assertEquals("VF", mediaLanguage("Lecteur VF", emptyList()))
        assertEquals("OTHER", mediaLanguage("Lecteur VO", emptyList()))
        assertFalse(acceptsMediaLanguage("VF", mediaLanguage("English VO", emptyList())))
        assertFalse(audioMatchesRequested("VF", null, listOf("ca", "es")))
        assertTrue(audioMatchesRequested("VF", null, listOf("fr", "en")))
        assertTrue(audioMatchesRequested("VF", null, listOf("fr-FR")))
        assertTrue(audioMatchesRequested("VF", null, listOf("fre")))
        // No rendition at all: the player cannot contradict the label here.
        assertTrue(audioMatchesRequested("VF", null, emptyList()))
    }

    @Test fun vostfrRequiresTheOriginalAudioAndNeverTheFrenchDub() {
        assertTrue(audioMatchesRequested("VOSTFR", "ja", listOf("ja", "fr")))
        assertFalse(audioMatchesRequested("VOSTFR", "ja", listOf("fr", "en")))
        assertFalse(audioMatchesRequested("VOSTFR", null, listOf("fr")))
        assertTrue(audioMatchesRequested("VOSTFR", null, listOf("en")))
        assertTrue(audioMatchesRequested("VOSTFR", "ko", listOf("ko-KR")))
        assertFalse(audioMatchesRequested("VOSTFR", "fr", listOf("fr")))
    }

    @Test fun vostfrNeedsFrenchSubtitlesWhenSubtitlesAreDeclared() {
        assertTrue(subtitlesMatchRequested("VOSTFR", emptyList()))
        assertTrue(subtitlesMatchRequested("VOSTFR", listOf("fr", "en")))
        assertFalse(subtitlesMatchRequested("VOSTFR", listOf("en")))
        assertTrue(subtitlesMatchRequested("VF", listOf("en")))
    }

    @Test fun languageTagsAreNormalised() {
        assertEquals("fr", mediaLanguageTag("fr-FR"))
        assertEquals("fr", mediaLanguageTag("FRE"))
        assertEquals("ja", mediaLanguageTag("jpn"))
        assertEquals("zh", mediaLanguageTag("zh-Hans"))
        assertNull(mediaLanguageTag("und"))
        assertNull(mediaLanguageTag(" "))
    }

    @Test fun manifestRenditionsAreReadAndUnknownLanguageIsRejected() {
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Français",LANGUAGE="fr",DEFAULT=YES
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Japanese",LANGUAGE="ja"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="sub",NAME="Français",LANGUAGE="fr"
            #EXT-X-STREAM-INF:BANDWIDTH=1,AUDIO="aud",SUBTITLES="sub"
            video.m3u8
        """.trimIndent()
        val info = parseHlsLanguageInfo(master)!!
        assertEquals(listOf("fr", "ja"), info.audio)
        assertEquals(listOf("fr"), info.subtitles)
        assertNull(parseHlsLanguageInfo("<html>captcha</html>"))
        // A single-audio manifest declares nothing, so it is not flagged as a mismatch.
        val media = parseHlsLanguageInfo("#EXTM3U\n#EXTINF:6,\nseg.ts")!!
        assertTrue(media.audio.isEmpty())
    }
}
