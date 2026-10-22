package com.stever.scribdownloader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlAnalyzerTest {

    @Test
    fun `document page url is accepted verbatim`() {
        val r = UrlAnalyzer.analyze("https://www.scribd.com/document/123/title-here")
        assertEquals(123L, r!!.docId)
        assertEquals("https://www.scribd.com/document/123/title-here", r.pasteUrl)
        assertEquals(UrlKind.DOCUMENT, r.kind)
    }

    @Test
    fun `legacy doc path is accepted verbatim`() {
        val r = UrlAnalyzer.analyze("https://www.scribd.com/doc/98")
        assertEquals(98L, r!!.docId)
        assertEquals(UrlKind.DOCUMENT, r.kind)
        assertEquals("https://www.scribd.com/doc/98", r.pasteUrl)
    }

    @Test
    fun `other scribd subdomains keep the full url as pasted`() {
        val r = UrlAnalyzer.analyze("https://es.scribd.com/document/55/doc-name")
        assertEquals(55L, r!!.docId)
        assertEquals("https://es.scribd.com/document/55/doc-name", r.pasteUrl)
    }

    @Test
    fun `embeds url is normalized to canonical doc form`() {
        val r = UrlAnalyzer.analyze("https://www.scribd.com/embeds/777/content?start_page=1&view_mode=scroll#f")
        assertEquals(777L, r!!.docId)
        assertEquals(UrlKind.EMBEDS, r.kind)
        assertEquals("https://www.scribd.com/doc/777", r.pasteUrl)
    }

    @Test
    fun `read book audiobook url kinds`() {
        assertEquals(UrlKind.READ, UrlAnalyzer.analyze("https://www.scribd.com/read/456/abc")!!.kind)
        assertEquals(456L, UrlAnalyzer.analyze("https://www.scribd.com/read/456/abc")!!.docId)
        assertEquals(UrlKind.BOOK, UrlAnalyzer.analyze("https://www.scribd.com/book/789/x")!!.kind)
        assertEquals(UrlKind.AUDIOBOOK, UrlAnalyzer.analyze("https://www.scribd.com/audiobook/999/y")!!.kind)
    }

    @Test
    fun `http scheme is upgraded to https`() {
        val r = UrlAnalyzer.analyze("http://www.scribd.com/document/10/t")
        assertEquals("https://www.scribd.com/document/10/t", r!!.pasteUrl)
    }

    @Test
    fun `schemeless input is accepted`() {
        val r = UrlAnalyzer.analyze("  www.scribd.com/document/11/t  ")
        assertEquals(11L, r!!.docId)
    }

    @Test
    fun `plain host without document id is rejected`() {
        assertNull(UrlAnalyzer.analyze("https://www.scribd.com/"))
        assertNull(UrlAnalyzer.analyze("https://www.scribd.com/search"))
    }

    @Test
    fun `non scribd hosts are rejected`() {
        assertNull(UrlAnalyzer.analyze("https://example.com/document/123/x"))
        assertNull(UrlAnalyzer.analyze("https://scribd.evil.com/document/123/x"))
        assertNull(UrlAnalyzer.analyze("https://notscribd.com/doc/123/x"))
    }

    @Test
    fun `empty and garbage inputs are rejected`() {
        assertNull(UrlAnalyzer.analyze(""))
        assertNull(UrlAnalyzer.analyze("   "))
        assertNull(UrlAnalyzer.analyze(":::not a url:::"))
    }

    @Test
    fun `app subdomain with id works`() {
        val r = UrlAnalyzer.analyze("https://app.scribd.com/document/300/abc")
        assertEquals(300L, r!!.docId)
        assertTrue(r.pasteUrl.startsWith("https://app.scribd.com/"))
    }

    @Test
    fun `sanitize keeps accents and collapses spaces`() {
        assertEquals("Café_Title", UrlAnalyzer.sanitizeFileName("Ca/fé?? : Title", 42L))
    }

    @Test
    fun `sanitize falls back when nothing survives`() {
        assertEquals("Scribd_42", UrlAnalyzer.sanitizeFileName("### / *", 42L))
    }

    @Test
    fun `sanitize caps length at 120`() {
        val long = "word ".repeat(40).trim()
        assertTrue(UrlAnalyzer.sanitizeFileName(long, 1L).length <= 120)
    }
}
