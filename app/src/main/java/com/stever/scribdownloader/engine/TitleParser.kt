package com.stever.scribdownloader.engine

/**
 * Pure extraction of `<title>` (+ cleanup ported from content.js/Utils
 * getCleanFilename). Lives in the engine package alongside its JVM tests
 * (TitleParserTest — the test was the contract, TDD).
 */
object TitleParser {
    private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ENTITIES = listOf(
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&nbsp;" to " ",
    )
    private val PIPE_SCRIBD = Regex("""\|\s*Scribd\s*$""", RegexOption.IGNORE_CASE)
    private val SCRIBD_WORDS = Regex("""Scribd""", RegexOption.IGNORE_CASE)
    private val LEA = Regex("""Lea en lí[íi]nea""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("""\s+""")

    fun parseTitle(html: String, fallback: String): String {
        val raw = TITLE.find(html)?.groupValues?.getOrNull(1) ?: return fallback
        var t = raw
        ENTITIES.forEach { (from, to) -> t = t.replace(from, to) }
        t = t.replace(PIPE_SCRIBD, "")
            .replace(SCRIBD_WORDS, "")
            .replace(LEA, "")
            .replace(WHITESPACE, " ")
            .trim()
        return if (t.isEmpty()) fallback else t
    }
}
