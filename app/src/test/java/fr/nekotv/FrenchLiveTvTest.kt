package fr.nekotv

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class FrenchLiveTvTest {
    @Test fun frenchChannelsKeepAllPublishedHlsFallbacksWithoutMixingStations() {
        val doc = Jsoup.parse(javaClass.getResource("/tvradiozap.html")!!.readText(), FrenchLiveTv.BASE)
        val channels = FrenchLiveTv.channels(doc)
        assertEquals(listOf("TF1", "Arte", "BFM TV"), channels.map { it.first.title })
        assertEquals(2, channels.first { it.first.title == "Arte" }.second.size)
        assertEquals(2, channels.first { it.first.title == "BFM TV" }.second.size)
        assertTrue(channels.all { it.second.all { server -> server.name.contains("FR") } })
        doc.selectFirst("tr[data-idx=640] td:nth-child(5)")!!.text("Germany")
        assertFalse(FrenchLiveTv.channels(doc).any { it.first.title == "Arte" })
        doc.selectFirst("tr[data-idx=890] a[target=ecoute]")!!.attr("href", "javascript:alert(1)")
        assertFalse(FrenchLiveTv.channels(doc).any { it.first.title == "TF1" })
    }
    @Test fun televisionBitKeepsSportRowsWithAdditionalFlags() {
        val doc = Jsoup.parse(javaClass.getResource("/tvradiozap.html")!!.readText(), FrenchLiveTv.BASE)
        doc.selectFirst("tr[data-idx=640]")!!.attr("data-tv", "257")
        doc.selectFirst("tr[data-idx=890]")!!.attr("data-tv", "513")
        assertEquals(3, FrenchLiveTv.channels(doc).size)
        doc.selectFirst("tr[data-idx=640]")!!.attr("data-tv", "256")
        assertFalse(FrenchLiveTv.channels(doc).any { it.first.title == "Arte" })
    }
}
