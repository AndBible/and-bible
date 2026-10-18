package net.bible.sharedui.app.readingplan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingPlanHostTest {
    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    private val platform = object : ReadingPlanHostPlatform {
        override val isPortrait = true
        override fun localYmd(epochMs: Long) = Triple(1970, 1, 1)
        override fun withLocalYmd(epochMs: Long, year: Int, month1to12: Int, day: Int) = epochMs
        override fun openInstallZip(refs: List<DocumentRef>) = Unit
    }

    private val resets = mutableListOf<String>()

    private fun host(restored: ReadingPlanRecreationState? = null) = ReadingPlanHost(
        shell = shell,
        strings = iosStrings("en"),
        platform = platform,
        documentPicker = object : DocumentPicker {
            override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean) = emptyList<DocumentRef>()
            override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? = null
            override suspend fun openDocumentTree(): DocumentRef? = null
        },
        readingPlanControlProvider = { error("readingPlanControl must stay lazy") },
        speakControlProvider = { error("speakControl must stay lazy") },
        appScopeProvider = { error("appScope must stay lazy") },
        restored = restored,
        resetPlan = { resets += it },
    )

    @Test
    fun selectorResetForgetsLoadedPlan() {
        val host = host()
        host.planCodeLoaded = "p1"
        host.onSelectorReset("p1")
        assertNull(host.planCodeLoaded)
        assertTrue("p1" in host.resetReadingPlanCodes)
        assertEquals(listOf("p1"), resets)

        // A different plan's reset leaves the loaded one alone but is remembered as reset.
        host.planCodeLoaded = "p1"
        host.onSelectorReset("p2")
        assertEquals("p1", host.planCodeLoaded)
        assertTrue("p2" in host.resetReadingPlanCodes)
    }

    @Test
    fun nothingLoadedNothingToForget() {
        val host = host()
        host.onSelectorReset("p1")
        assertNull(host.planCodeLoaded)
        assertTrue("p1" in host.resetReadingPlanCodes)
    }

    @Test
    fun restoredResetCodesAreKept() {
        val host = host(ReadingPlanRecreationState(setOf("a", "b")))
        assertEquals(setOf("a", "b"), host.resetReadingPlanCodes)
        assertEquals(ReadingPlanRecreationState(setOf("a", "b")), host.recreationSnapshot())
    }
}
