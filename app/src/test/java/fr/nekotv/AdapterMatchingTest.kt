package fr.nekotv

import org.junit.Assert.*
import org.junit.Test

class AdapterMatchingTest {
    private fun fiche() = Anime(
        title = "Saiki Kusuo no Psi-nan",
        tag = "Série",
        episodes = emptyList(),
        year = 2011,
        aliases = listOf("The Disastrous Life of Saiki K.", "斉木楠雄のΨ難"),
        imdbId = "tt5683324"
    )

    @Test fun acceptsThreeLocalizedTitles() {
        val target = fiche()
        listOf("Saiki Kusuo no Psi-nan", "The Disastrous Life of Saiki K.", "斉木楠雄のΨ難").forEach { title ->
            assertTrue(AdapterMatching.decide(target, AdapterMatching.Candidate(title, 2011, "Série")).accepted)
        }
    }

    @Test fun rejectsSequelAndRemakeByYearOrType() {
        val target = fiche()
        assertFalse(AdapterMatching.decide(target, AdapterMatching.Candidate("The Disastrous Life of Saiki K. 2", 2012, "Série")).accepted)
        assertFalse(AdapterMatching.decide(target, AdapterMatching.Candidate("Saiki Kusuo no Psi-nan", 2024, "Film")).accepted)
    }

    @Test fun externalIdWinsWhenTitleIsLocalized() {
        val result = AdapterMatching.decide(fiche(), AdapterMatching.Candidate("斉木楠雄のΨ難", 2000, "Film", "tt5683324"))
        assertTrue(result.accepted)
        assertEquals("external-id", result.reason)
    }
}
