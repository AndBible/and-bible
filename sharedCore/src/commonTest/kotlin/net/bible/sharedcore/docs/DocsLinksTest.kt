package net.bible.sharedcore.docs

import kotlin.test.Test
import kotlin.test.assertEquals

class DocsLinksTest {
    @Test
    fun pageWithoutAnchor() =
        assertEquals("https://andbible.org/docs/study_pads/", DocsLink.STUDY_PADS.url)

    @Test
    fun pageWithAnchor() =
        assertEquals("https://andbible.org/docs/ai/#setting-permissions", DocsLink.AI_SETTING_PERMISSIONS.url)
}
