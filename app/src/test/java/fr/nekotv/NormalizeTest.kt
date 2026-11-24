package fr.nekotv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizeTest {
    @Test
    fun testNormalizeQuery() {
        // Test basic accent removal and case folding
        assertEquals("film l'histoire", Normalize.normalizeQuery("Film l'Histoire"))
        assertEquals("amelie poulain", Normalize.normalizeQuery("Amélie Poulain"))
        
        // Test article removal
        assertEquals("histoire", Normalize.normalizeQuery("la histoire"))
        assertEquals("anime", Normalize.normalizeQuery("un anime"))
        
        // Test punctuation stripping
        assertEquals("spider man", Normalize.normalizeQuery("Spider-Man!"))
        
        // Test mixed case and accents
        assertEquals("seigneur anneaux", Normalize.normalizeQuery("Le SEIGNEUR des Anneaux"))
    }

    @Test
    fun testTokens() {
        val tokens = Normalize.tokens("Harry Potter et la Chambre des Secrets")
        // Should remove 'et', 'la', 'des'
        assertTrue(tokens.contains("harry"))
        assertTrue(tokens.contains("potter"))
        assertTrue(tokens.contains("chambre"))
        assertTrue(tokens.contains("secrets"))
        assertEquals(4, tokens.size)
    }

    @Test
    fun testCacheKey() {
        val q1 = "  Amélie Poulain  "
        val q2 = "amelie poulain"
        assertEquals(Normalize.cacheKey(q1), Normalize.cacheKey(q2))
    }
}
