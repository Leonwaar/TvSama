package fr.nekotv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePreferenceTest {
    private fun source(provider: String, name: String, url: String, language: String = "VOSTFR",
        serverId: String = provider, quality: String = "Auto") =
        VideoSource(name, url, language, quality, provider, serverId = serverId)

    @Test fun nextEpisodeReusesTheChosenProviderAndServerEvenWhenRankingChanges() {
        val chosen = source("MyFluneo", "LECTEUR VOE VOSTFR", "https://old/episode-1.m3u8", serverId = "voe")
        val ranked = listOf(
            source("Vidrift", "VOSTFR", "https://vidrift/episode-2.m3u8"),
            source("MyFluneo", "LECTEUR myTV VOSTFR", "https://other/episode-2.m3u8", serverId = "mytv"),
            source("MyFluneo", "LECTEUR VOE VOSTFR", "https://new/episode-2.m3u8", serverId = "voe"))
        assertEquals("https://new/episode-2.m3u8", choosePlaybackSource(ranked, chosen)?.url)
    }

    @Test fun sameProviderIsPreferredWhenTheExactServerDisappears() {
        val chosen = source("MyFluneo", "LECTEUR VOE VOSTFR", "https://old.m3u8", serverId = "voe")
        val ranked = listOf(source("Vidrift", "VOSTFR", "https://vidrift.m3u8"),
            source("MyFluneo", "LECTEUR Stape VOSTFR", "https://stape.m3u8", serverId = "stape"))
        assertEquals("https://stape.m3u8", choosePlaybackSource(ranked, chosen)?.url)
    }

    @Test fun switchingLanguageIgnoresThePreviousSource() {
        val vf = source("MyFluneo", "LECTEUR VOE VF", "https://vf.m3u8", language = "VF", serverId = "voe")
        val vostfr = source("MyFluneo", "LECTEUR VOE VOSTFR", "https://vostfr.m3u8", serverId = "voe")
        assertEquals(vostfr, choosePlaybackSource(listOf(vostfr), vf, "VOSTFR"))
    }

    @Test fun firstRankedSourceIsUsedWithoutAPreference() {
        val first = source("A", "A", "https://a.m3u8")
        assertEquals(first, choosePlaybackSource(listOf(first, source("B", "B", "https://b.m3u8")), null))
        assertNull(choosePlaybackSource(emptyList(), first))
    }
}

class CatalogueIdentityFilmTest {
    @Test fun providerThatCallsAFilmASeriesMergesWithTheFilmCard() {
        val seriesCopy = Anime("Hunter x Hunter Movie 2: The Last Mission", "Série", emptyList(), "series",
            provider = "AnimeSite")
        val filmCopy = Anime("Hunter X Hunter: The Last Mission", "Film", emptyList(), "film",
            provider = "FRAnime", year = null)
        val merged = CatalogIdentity.merge(listOf(seriesCopy, filmCopy))
        assertEquals(1, merged.size)
        assertEquals("Film", merged.single().tag)
        assertEquals(setOf("AnimeSite", "FRAnime"), merged.single().references.map { it.provider }.toSet())
    }

    @Test fun identicalFilmsGroupByTheirTmdbIdEvenWithDifferentTitles() {
        val french = Anime("Le Voyage de Chihiro", "Série", emptyList(), "fr", provider = "A", tmdbId = 129)
        val english = Anime("Spirited Away", "Film", emptyList(), "en", provider = "B", tmdbId = 129)
        val merged = CatalogIdentity.merge(listOf(french, english))
        assertEquals(1, merged.size)
        assertEquals(setOf("A", "B"), merged.single().references.map { it.provider }.toSet())
    }

    @Test fun remakesWithTheSameFilmTitleStaySeparate() {
        val old = Anime("Dune", "Film", emptyList(), "old", provider = "A", year = 1984)
        val recent = Anime("Dune", "Film", emptyList(), "new", provider = "B", year = 2021)
        assertEquals(2, CatalogIdentity.merge(listOf(old, recent)).size)
    }

    @Test fun numberedFilmsWithoutARealTitleStaySeparate() {
        val first = Anime("Hunter x Hunter Movie 1", "Série", emptyList(), "one", provider = "A")
        val second = Anime("Hunter x Hunter Movie 2", "Série", emptyList(), "two", provider = "A")
        assertEquals(2, CatalogIdentity.merge(listOf(first, second)).size)
    }

    @Test fun numberedMarkerWithARealTitleStillMatchesTheUnnumberedFilm() {
        val numbered = Anime("Hunter x Hunter Movie 2: The Last Mission", "Série", emptyList(), "one", provider = "A")
        val plain = Anime("Hunter X Hunter: The Last Mission", "Film", emptyList(), "two", provider = "B")
        assertEquals(1, CatalogIdentity.merge(listOf(numbered, plain)).size)
    }
}

class CatalogueAppendStabilityTest {
    private fun cardKey(anime: Anime) = "${anime.provider}|${anime.tag}|${anime.id}"

    @Test fun filmArrivingInALaterCommitCollapsesIntoTheExistingSeriesCard() {
        val series = Anime("Hunter x Hunter Movie 2: The Last Mission", "Série", emptyList(), "series",
            provider = "AnimeSite")
        val film = Anime("Hunter X Hunter: The Last Mission", "Film", emptyList(), "film", provider = "FRAnime")
        val first = CatalogIdentity.appendStable(emptyList(), listOf(series))
        val second = CatalogIdentity.appendStable(first, listOf(film))
        assertEquals(1, second.size)
        assertEquals(second.map(::cardKey).distinct().size, second.size)
        assertEquals(setOf("AnimeSite", "FRAnime"), second.single().references.map { it.provider }.toSet())
    }

    @Test fun alreadyCommittedCardsKeepTheirPositionWhenAnotherProviderAnswersLate() {
        val a = Anime("Alpha", "Série", emptyList(), "a", provider = "P")
        val b = Anime("Beta", "Série", emptyList(), "b", provider = "P")
        val c = Anime("Gamma", "Série", emptyList(), "c", provider = "Q")
        val first = CatalogIdentity.appendStable(emptyList(), listOf(a, b))
        val second = CatalogIdentity.appendStable(first, listOf(c, b, a))
        assertEquals(listOf("a", "b", "c"), second.map { it.id })
        assertEquals(second.map(::cardKey).distinct().size, second.size)
    }

    @Test fun reversingTheCommitOrderDoesNotDuplicateOrReorder() {
        val a = Anime("Alpha", "Film", emptyList(), "a", provider = "P", tmdbId = 1)
        val b = Anime("Beta", "Film", emptyList(), "b", provider = "Q", tmdbId = 2)
        val first = CatalogIdentity.appendStable(emptyList(), listOf(a))
        val second = CatalogIdentity.appendStable(first, listOf(b))
        val third = CatalogIdentity.appendStable(second, listOf(b, a))
        assertEquals(listOf("a", "b"), third.map { it.id })
        assertEquals(third.map(::cardKey).distinct().size, third.size)
    }

    @Test fun sameProviderIdIsNeverPresentTwiceEvenWhenTitlesDiffer() {
        val original = Anime("Beta", "Film", emptyList(), "same", provider = "P")
        val variant = Anime("Beta (VF)", "Film", emptyList(), "same", provider = "P")
        val merged = CatalogIdentity.appendStable(CatalogIdentity.appendStable(emptyList(), listOf(original)), listOf(variant))
        assertEquals(merged.map(::cardKey).distinct().size, merged.size)
    }
}

class HunterHunterIdentityTest {
    @Test fun commonHunterHunterSpellingsShareOneIdentity() {
        assertEquals(CatalogIdentity.title("Hunter x Hunter"), CatalogIdentity.title("HxH"))
        assertEquals(CatalogIdentity.title("Hunter x Hunter"), CatalogIdentity.title("Hunter × Hunter"))
        assertNotEquals(CatalogIdentity.title("Hunter x Hunter"), CatalogIdentity.title("Hunter x Hunter 2011"))
        assertEquals(CatalogIdentity.identityTitle("Hunter x Hunter"), CatalogIdentity.identityTitle("Hunter x Hunter 2011"))
        assertEquals(CatalogIdentity.identityTitle("Hunter x Hunter"), CatalogIdentity.identityTitle("Hunter x Hunter (2011)"))
        assertEquals(CatalogIdentity.identityTitle("Hunter x Hunter"), CatalogIdentity.identityTitle("Hunter x Hunter Kai"))
    }

    @Test fun datedEditionDoesNotJoinAnUnknownEditionThroughAnAlias() {
        val target = Anime("Hunter x Hunter", "Série", emptyList(), "200", provider = "Anime-Sama")
        val dated = Anime("Hunter x Hunter 2011", "Série", emptyList(), "2011", provider = "FRAnime",
            aliases = listOf("Hunter x Hunter"))
        assertTrue(relatedCatalogueMatches(target, listOf(dated)).isEmpty())
        val sameEdition = Anime("Hunter x Hunter", "Série", emptyList(), "1999", provider = "MyFluneo")
        assertEquals(1, relatedCatalogueMatches(target, listOf(sameEdition)).size)
    }
}
