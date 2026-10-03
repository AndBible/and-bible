package net.bible.sharedcore.docs

import kotlin.test.Test
import kotlin.test.assertEquals

class DocsLinksTest {
    @Test
    fun pageWithoutAnchor() =
        assertEquals("https://andbible.org/docs/study_pads/", DocsLinks.page("study_pads"))

    @Test
    fun pageWithAnchor() =
        assertEquals("https://andbible.org/docs/ai/#setting-permissions", DocsLinks.page("ai", "setting-permissions"))
}
