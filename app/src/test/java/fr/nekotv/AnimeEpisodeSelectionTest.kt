package fr.nekotv

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeEpisodeSelectionTest {
    private val vostfr = Episode("Épisode 1 VOSTFR", "", emptyList(), "vo", 1, 1, language = "VOSTFR")
    private val vf = Episode("Épisode 1 VF", "", emptyList(), "vf", 1, 1, language = "VF")
    private val selected = Episode("Épisode 1", "", emptyList(), "other-provider", 1, 1)

    @Test fun languageSelectionKeepsOnlyItsReaderEvenWhenIdsDifferBetweenProviders() {
        assertEquals(listOf("vf"), episodeCandidates(listOf(vostfr, vf), selected, "VF").map { it.id })
    }

    @Test fun requestedFrenchReaderNeverFallsBackToSubtitledReader() {
        assertEquals(listOf("vf"), episodeCandidates(listOf(vostfr, vf), selected, "VF").map { it.id })
    }

    @Test fun unknownReaderRemainsAvailableWhenLanguageIsNotLabeled() {
        assertEquals(listOf("unknown"), episodeCandidates(listOf(vostfr, vf.copy(id = "unknown", language = "UNKNOWN")), selected, "VF").map { it.id })
    }
}
