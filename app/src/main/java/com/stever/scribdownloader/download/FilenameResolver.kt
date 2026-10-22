package com.stever.scribdownloader.download

/**
 * Keeps generated file names unique inside the shared Downloads folder
 * (100-char-safe: capped base so the suffix always fits, Task 6).
 */
object FilenameResolver {

    fun uniquify(want: String, taken: Set<String>): String {
        if (want !in taken) return want

        val dot = want.lastIndexOf('.')
        val ext = if (dot > 0) want.substring(dot) else ""
        val base = if (dot > 0) want.substring(0, dot) else want

        var i = 1
        while (true) {
            val suffix = "_$i"
            val candidateBase = base.take(MAX_BASE_LENGTH - suffix.length) + suffix
            val candidate = candidateBase + ext
            if (candidate !in taken) return candidate
            i++
        }
    }

    private const val MAX_BASE_LENGTH = 116 // base + "_NNN" stays ≤ 119, + ".pdf" ≤ 123
}
