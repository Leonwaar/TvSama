package fr.nekotv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MetadataUtilsTest {

    @Test
    fun `test synopsis fallback chain`() {
        // 1. Native French
        val animeNative = Anime(title = "T1", tag = "Film", episodes = emptyList(), description = "Ceci est un synopsis français.")
        assertEquals("Ceci est un synopsis français.", SynopsisResolver.resolveOverview(animeNative).text)
        assertEquals("native", SynopsisResolver.resolveOverview(animeNative).source)

        // 2. False French (English in description)
        val animeFalseFr = Anime(title = "T2", tag = "Film", episodes = emptyList(), description = "This is an English synopsis")
        // Should fallback to "machine" translation placeholder
        assertEquals("Traduction automatique de : This is an English synopsis", SynopsisResolver.resolveOverview(animeFalseFr).text)
        assertEquals("machine", SynopsisResolver.resolveOverview(animeFalseFr).source)

        // 3. Fallback to descriptions map (merged sources)
        val animeMerged = Anime(
            title = "T3", tag = "Film", episodes = emptyList(), 
            description = "English primary", 
            descriptions = mapOf("ProviderB" to "Synopsis français secondaire")
        )
        assertEquals("Synopsis français secondaire", SynopsisResolver.resolveOverview(animeMerged).text)
        assertEquals("native", SynopsisResolver.resolveOverview(animeMerged).source)

        // 4. No French available
        val animeEmpty = Anime(title = "T4", tag = "Film", episodes = emptyList(), description = "")
        assertEquals("Le synopsis n'est pas disponible en français.", SynopsisResolver.resolveOverview(animeEmpty).text)
        assertEquals("none", SynopsisResolver.resolveOverview(animeEmpty).source)
    }

    @Test
    fun `test release info formatting`() {
        // Film
        val movie = Anime(title = "M1", tag = "Film", episodes = emptyList(), firstAired = "2024-02-14")
        assertEquals("Sortie le 14 février 2024", ReleaseInfoFormatter.formatReleaseInfo(movie))

        val movieYear = Anime(title = "M2", tag = "Film", episodes = emptyList(), firstAired = "2024")
        assertEquals("2024", ReleaseInfoFormatter.formatReleaseInfo(movieYear)) // Year-only shows just the year

        // Series - Ongoing
        val seriesOngoing = Anime(title = "S1", tag = "Série", episodes = emptyList(), firstAired = "2022-01-01", status = "en_cours")
        assertEquals("2022 – en cours", ReleaseInfoFormatter.formatReleaseInfo(seriesOngoing))

        // Series - Terminated
        val seriesTerminated = Anime(title = "S2", tag = "Série", episodes = emptyList(), firstAired = "2011-04-17", endDate = "2019-05-19", status = "terminee")
        assertEquals("Du 17 avril 2011 au 19 mai 2019", ReleaseInfoFormatter.formatReleaseInfo(seriesTerminated, "full"))
        assertEquals("2011 – 2019", ReleaseInfoFormatter.formatReleaseInfo(seriesTerminated, "compact"))

        // Series - Canceled
        val seriesCanceled = Anime(title = "S3", tag = "Série", episodes = emptyList(), firstAired = "2016-01-01", endDate = "2018-12-31", status = "annulee")
        assertEquals("2016 · Annulée", ReleaseInfoFormatter.formatReleaseInfo(seriesCanceled))

        // Series - Upcoming
        val seriesUpcoming = Anime(title = "S4", tag = "Série", episodes = emptyList(), firstAired = "2027-03-03", status = "a_venir")
        assertEquals("Prévue le 3 mars 2027", ReleaseInfoFormatter.formatReleaseInfo(seriesUpcoming))

        // Unknown
        val seriesUnknown = Anime(title = "S5", tag = "Série", episodes = emptyList(), firstAired = null)
        assertEquals("Date non communiquée", ReleaseInfoFormatter.formatReleaseInfo(seriesUnknown))
    }
}
