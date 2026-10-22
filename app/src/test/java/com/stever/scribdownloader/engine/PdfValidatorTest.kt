package com.stever.scribdownloader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfValidatorTest {

    @Test
    fun `pdf with big size is ok`() {
        assertEquals(PdfEligibility.OK, PdfValidator.evaluate("application/pdf", 5000L))
    }

    @Test
    fun `octet stream with big size is ok`() {
        assertEquals(PdfEligibility.OK, PdfValidator.evaluate("application/octet-stream", 30000L))
        assertEquals(PdfEligibility.OK, PdfValidator.evaluate("application/force-download", 2049L))
    }

    @Test
    fun `octet stream with tiny size is too small`() {
        assertEquals(PdfEligibility.TOO_SMALL, PdfValidator.evaluate("application/octet-stream", 1000L))
    }

    @Test
    fun `boundary exactly 2048 is too small`() {
        assertEquals(PdfEligibility.TOO_SMALL, PdfValidator.evaluate("application/pdf", 2048L))
    }

    @Test
    fun `unknown size trusts content type`() {
        assertEquals(PdfEligibility.OK, PdfValidator.evaluate("application/pdf", null))
        assertEquals(PdfEligibility.OK, PdfValidator.evaluate("application/pdf", -1L))
    }

    @Test
    fun `non pdf types are rejected`() {
        assertEquals(PdfEligibility.BAD_TYPE, PdfValidator.evaluate("text/html", 999999L))
        assertEquals(PdfEligibility.BAD_TYPE, PdfValidator.evaluate(null, null))
    }
}
