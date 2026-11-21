package fr.nekotv

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LiveReaderFallbackTest {
    @Test fun frenchReaderWinsOverFasterEnglishReaderAndEnglishRemainsFallback() = runBlocking {
        val readers = listOf("English", "Français")
        val priority: (String) -> Int = { if (it == "Français") 0 else 2 }
        assertEquals("Français", firstLiveReaderByLanguage(readers, 100, priority) {
            if (it == "Français") delay(30)
            it
        })
        assertEquals("English", firstLiveReaderByLanguage(readers, 100, priority) {
            if (it == "Français") null else it
        })
    }
    @Test fun secondReaderStartsEvenWhenFirstReaderStalls() = runBlocking {
        val started = mutableSetOf<Int>()
        val result = firstLiveReader(listOf(1, 2), 100) { reader ->
            started += reader
            if (reader == 1) { delay(1000); "first" } else { delay(10); "second" }
        }
        assertEquals("second", result)
        assertEquals(setOf(1, 2), started)
    }
    @Test fun failedReaderDoesNotHideWorkingReaderAndOuterCancellationPropagates() = runBlocking {
        assertEquals("second", firstLiveReader(listOf(1, 2), 100) { reader -> if (reader == 1) error("source down") else "second" })
        assertNull(firstLiveReader(listOf(1), 100) { withTimeout(5) { delay(100); "late" } })
        assertNull(firstLiveReader(listOf(1, 2), 10) { delay(100); "late" })
        assertNull(withTimeoutOrNull(10) { firstLiveReader(listOf(1, 2), 1000) { delay(100); "late" } })
    }
}
