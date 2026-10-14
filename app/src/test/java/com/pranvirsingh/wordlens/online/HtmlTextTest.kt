package com.pranvirsingh.wordlens.online

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTextTest {

    @Test
    fun `named and numbered entities become characters`() {
        assertEquals("A & B — “C” – D…", HtmlText.decode("A &amp; B &mdash; &ldquo;C&rdquo; &ndash; D&hellip;"))
        assertEquals("it's — é", HtmlText.decode("it&#39;s &#8212; &#xE9;"))
    }

    @Test
    fun `unknown or broken entities are left alone`() {
        assertEquals("&bogus; &#99999999; & plain", HtmlText.decode("&bogus; &#99999999; & plain"))
    }
}
