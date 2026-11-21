package fr.nekotv

import org.junit.Assert.*
import org.junit.Test

class EpisodeAdvanceTest {
    @Test fun skipIntroAndOutroRespectActualPlaybackBounds() {
        val duration = 1_374_000L
        val intro = boundedSegment(IntroSegment(0, 91_000), duration, false)
        val outro = boundedSegment(IntroSegment(1_281_000, 1_375_000), duration, true)
        assertEquals(IntroSegment(0, 91_000), intro)
        assertEquals(IntroSegment(1_281_000, duration), outro)
        assertTrue(90_999 in intro!!.start until intro.end)
        assertFalse(91_000 in intro.start until intro.end)
        assertTrue(1_281_000 in outro!!.start until outro.end)
        assertFalse(duration in outro.start until outro.end)
        assertTrue(showNextEpisodePrompt(1_271_000, duration, outro))
        assertFalse(EpisodeAdvance().claim(1_280_999, duration, outro, true))
        assertTrue(EpisodeAdvance().claim(1_281_000, duration, outro, true))
    }
    @Test fun postCreditsSceneDelaysAutomaticNextEpisode() {
        val segments = EpisodeSegments(outro = IntroSegment(1_300_000, 1_350_000), postCredits = IntroSegment(1_350_000, 1_420_000))
        val threshold = segments.autoOutro(1_450_000)
        assertEquals(IntroSegment(1_420_000, 1_450_000), threshold)
        assertFalse(EpisodeAdvance().claim(1_419_999, 1_450_000, threshold, true))
        assertTrue(EpisodeAdvance().claim(1_420_000, 1_450_000, threshold, true))
    }
    @Test fun fallsBackToEndAndOnlyAdvancesOnce() {
        val advance = EpisodeAdvance()
        assertFalse(advance.claim(69_999, 100_000, null, true))
        assertFalse(advance.claim(70_000, 100_000, null, true))
        assertTrue(advance.claim(100_000, 100_000, null, true))
        assertFalse(advance.claim(90_000, 100_000, null, true))
        assertFalse(advance.claim(100_000, 100_000, null, true))
    }
    @Test fun usesPublishedOutroInsteadOfCuttingTheStoryThirtySecondsEarly() {
        val outro = IntroSegment(60_000, 95_000)
        assertFalse(EpisodeAdvance().claim(59_999, 100_000, outro, true))
        assertTrue(EpisodeAdvance().claim(60_000, 100_000, outro, true))
        assertFalse(EpisodeAdvance().claim(70_000, 100_000, IntroSegment(90_000, 99_000), true))
        assertTrue(EpisodeAdvance().claim(90_000, 100_000, IntroSegment(90_000, 99_000), true))
    }
    @Test fun invalidOutrosUseFallback() {
        for (outro in listOf(IntroSegment(-1, 99_000), IntroSegment(40_000, 30_000),
                IntroSegment(40_000, 110_000), IntroSegment(0, 90_000))) {
            assertFalse(EpisodeAdvance().claim(60_000, 100_000, outro, true))
            assertTrue(EpisodeAdvance().claim(100_000, 100_000, outro, true))
        }
    }
    @Test fun disabledPausedMissingNextAndUnknownDurationsDoNotAdvance() {
        val advance = EpisodeAdvance()
        assertFalse(advance.claim(90_000, 100_000, null, false))
        assertFalse(advance.claim(90_000, -1, null, true))
        assertFalse(advance.claim(-1, 100_000, null, true))
        assertTrue(advance.claim(100_000, 100_000, null, true))
    }
    @Test fun slightOutroDurationDifferencesAreClampedAndPromptAppearsTwoMinutesBeforeEnd() {
        assertEquals(IntroSegment(1_281_000, 1_374_000), boundedSegment(IntroSegment(1_281_000, 1_375_000), 1_374_000, true))
        assertNull(boundedSegment(IntroSegment(1000, 50_000), 1_374_000, true))
        assertNull(boundedSegment(IntroSegment(0, 900_000), 1_374_000, false))
        assertEquals(IntroSegment(441_389, 467_677), boundedSegment(IntroSegment(441_389, 467_677), 2_585_000, false))
        assertFalse(showNextEpisodePrompt(1_253_999, 1_374_000, null))
        assertTrue(showNextEpisodePrompt(1_254_000, 1_374_000, null))
        assertFalse(showNextEpisodePrompt(90_000, 100_000, null))
        assertFalse(showNextEpisodePrompt(1_374_000, 1_374_000, null))
    }
    @Test fun shortVideosAreNotSkippedAtStart() {
        assertFalse(EpisodeAdvance().claim(1, 12_000, null, true))
        assertFalse(EpisodeAdvance().claim(11_999, 12_000, null, true))
        assertTrue(EpisodeAdvance().claim(12_000, 12_000, null, true))
    }
    @Test fun postCreditsAreReachedOnceAndPromptWaitsForSceneEnd() {
        val segments = EpisodeSegments(outro = IntroSegment(1_300_000, 1_350_000), postCredits = IntroSegment(1_350_000, 1_420_000))
        val advance = EpisodeAdvance()
        assertNull(advance.claimPostCredits(1_300_000, 1_450_000, segments, false))
        assertEquals(1_350_000L, advance.claimPostCredits(1_300_000, 1_450_000, segments, true))
        assertNull(advance.claimPostCredits(1_300_000, 1_450_000, segments, true))
        assertFalse(showNextEpisodePrompt(1_419_999, 1_450_000, segments.outro, segments.postCredits))
        assertTrue(showNextEpisodePrompt(1_420_000, 1_450_000, segments.outro, segments.postCredits))
        assertFalse(showNextEpisodePrompt(1_269_999, 1_450_000, segments.outro))
        assertTrue(showNextEpisodePrompt(1_270_000, 1_450_000, segments.outro))
    }
}
