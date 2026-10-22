package com.stever.scribdownloader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleParserTest {

    @Test
    fun `strips pipe scribd suffix`() {
        assertEquals(
            "My Doc",
            TitleParser.parseTitle("<html><title>My Doc | Scribd</title></html>", "fb"),
        )
    }

    @Test
    fun `decodes basic html entities`() {
        assertEquals(
            "A & B 'quoted'",
            TitleParser.parseTitle("<title>A &amp; B &#39;quoted&#39;</title>", "fb"),
        )
    }

    @Test
    fun `missing title returns fallback`() {
        assertEquals("fb", TitleParser.parseTitle("<html><body>no title</body></html>", "fb"))
    }

    @Test
    fun `empty title returns fallback`() {
        assertEquals("fb", TitleParser.parseTitle("<title>   </title>", "fb"))
    }

    @Test
    fun `lea en linea and scribd words removed`() {
        assertEquals(
            "Mi Documento",
            TitleParser.parseTitle("<title>Lea en línea Mi Documento | Scribd</title>", "fb"),
        )
    }

    @Test
    fun `case insensitive title tag`() {
        assertEquals(
            "Document",
            TitleParser.parseTitle("<TITLe>Document</TITLE>", "fb"),
        )
    }
}
