package fr.nekotv

import org.junit.Assert.*
import org.junit.Test

class EpisodeAdvanceTest {
    @Test fun fallsBackToThirtySecondsAndOnlyAdvancesOnce() {
        val advance = EpisodeAdvance()
        assertFalse(advance.claim(69_999, 100_000, null, true))
        assertTrue(advance.claim(70_000, 100_000, null, true))
        assertFalse(advance.claim(90_000, 100_000, null, true))
        assertFalse(advance.claim(100_000, 100_000, null, true))
    }
    @Test fun usesEarlierOutroAndNeverWaitsPastThirtySecondsRemaining() {
        val outro = IntroSegment(60_000, 95_000)
        assertFalse(EpisodeAdvance().claim(59_999, 100_000, outro, true))
        assertTrue(EpisodeAdvance().claim(60_000, 100_000, outro, true))
        assertTrue(EpisodeAdvance().claim(70_000, 100_000, IntroSegment(90_000, 99_000), true))
    }
    @Test fun invalidOutrosUseFallback() {
        for (outro in listOf(IntroSegment(-1, 99_000), IntroSegment(40_000, 30_000),
                IntroSegment(40_000, 110_000), IntroSegment(0, 90_000))) {
            assertFalse(EpisodeAdvance().claim(60_000, 100_000, outro, true))
            assertTrue(EpisodeAdvance().claim(70_000, 100_000, outro, true))
        }
    }
    @Test fun disabledPausedMissingNextAndUnknownDurationsDoNotAdvance() {
        val advance = EpisodeAdvance()
        assertFalse(advance.claim(90_000, 100_000, null, false))
        assertFalse(advance.claim(90_000, -1, null, true))
        assertFalse(advance.claim(-1, 100_000, null, true))
        assertTrue(advance.claim(90_000, 100_000, null, true))
    }
    @Test fun shortVideosAreNotSkippedAtStart() {
        assertFalse(EpisodeAdvance().claim(1, 12_000, null, true))
        assertFalse(EpisodeAdvance().claim(11_999, 12_000, null, true))
        assertTrue(EpisodeAdvance().claim(12_000, 12_000, null, true))
    }
}
