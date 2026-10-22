package com.stever.scribdownloader.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameResolverTest {

    @Test
    fun `free name passes through`() {
        assertEquals("report.pdf", FilenameResolver.uniquify("report.pdf", emptySet()))
    }

    @Test
    fun `taken name gets suffix`() {
        assertEquals("a_1.pdf", FilenameResolver.uniquify("a.pdf", setOf("a.pdf")))
    }

    @Test
    fun `suffix increments until free`() {
        assertEquals(
            "a_2.pdf",
            FilenameResolver.uniquify("a.pdf", setOf("a.pdf", "a_1.pdf")),
        )
    }

    @Test
    fun `name without extension still gets suffix`() {
        assertEquals("doc_1", FilenameResolver.uniquify("doc", setOf("doc")))
    }

    @Test
    fun `free short name ignores other variants`() {
        assertEquals("arch.pdf", FilenameResolver.uniquify("arch.pdf", setOf("arch_1.pdf")))
    }

    @Test
    fun `very long names stay within the 120 chars budget plus extension`() {
        val long = "word_".repeat(30) + ".pdf"
        val res = FilenameResolver.uniquify(long, setOf(long))
        assertTrue(res.length <= 124)
    }

    @Test
    fun `result is not in taken set`() {
        val long = "word_".repeat(30) + ".pdf"
        val res = FilenameResolver.uniquify(long, setOf(long, FilenameResolver.uniquify(long, setOf(long))))
        assertTrue(res !in setOf(long, FilenameResolver.uniquify(long, setOf(long))))
    }
}
