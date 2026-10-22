package com.stever.scribdownloader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCodecTest {

    private val entry = HistoryEntry(
        id = 1000L,
        docId = 42L,
        title = "Doc \"Quote\" 😀 &fichier",
        fileName = "Doc_Quote_file.pdf",
        downloadId = 777L,
        completed = true,
        sizeBytes = 2048L + 1,
        addedAt = 1_700_000_000_000L,
    )

    @Test
    fun `encode decode roundtrip preserves every field`() {
        val json = HistoryCodec.encode(listOf(entry))
        val back = HistoryCodec.decode(json)
        assertEquals(1, back.size)
        assertEquals(entry, back[0])
    }

    @Test
    fun `encode of empty list is a valid empty json`() {
        assertEquals("[]", HistoryCodec.encode(emptyList()))
        assertTrue(HistoryCodec.decode("[]").isEmpty())
    }

    @Test
    fun `malformed json decodes to empty list`() {
        assertTrue(HistoryCodec.decode("not json at all").isEmpty())
        assertTrue(HistoryCodec.decode("{}").isEmpty())
        assertTrue(HistoryCodec.decode("""[{"id":"x"}]""").isEmpty())
    }

    @Test
    fun `entry missing optional fields still decodes with defaults`() {
        val partial = """[{"id":5,"docId":6,"title":"t","fileName":"t.pdf","downloadId":7,"completed":false,"sizeBytes":0,"addedAt":0}]"""
        val list = HistoryCodec.decode(partial)
        assertEquals(1, list.size)
        assertEquals(5L, list[0].id)
        assertEquals("t.pdf", list[0].fileName)
    }
}
