package net.bible.android.control.event

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Source-scan guard (the repo's `*GuardTest` idiom) pinning which stream and which variant
 * (`subscribe` vs `subscribeOnMain`) the phase 8 subscribers use. It does not prove handler bodies.
 */
class Phase8SubscriberWiringGuardTest {
    private fun src(path: String) =
        if (path.startsWith("../")) java.io.File(path).readText() // `:sharedUi` is not a ProductionSources root
        else net.bible.test.ProductionSources.resolve("net/bible/$path").readText()

    private fun assertContains(path: String, needle: String, why: String) =
        assertTrue(src(path).contains(needle), "$path must contain `$needle`: $why")

    @Test fun bibleViewListensToAddonsWorkspaceSettingsAndAiConfig() {
        val p = "android/view/activity/page/BibleView.kt"
        assertContains(p, "AndBibleAddons.reloaded.subscribe {", "reload_addons, synchronous like the old on{}")
        assertContains(p, "WorkspaceChanges.changes.subscribe { if (it == WorkspaceChange.SettingsEdited) updateConfig() }", "SettingsEdited reaches updateConfig")
        assertContains(p, "CommonUtils.llmConfiguredChanges.subscribe { updateConfig() }", "Review Focus 3: llmConfigured follows the refreshed value")
    }

    @Test fun sessionEventRelayKeepsEveryControllerSubscriptionOnTheEmitterThread() {
        // I2 Stream B Task 9 moved these five subscriptions out of BibleView.listenEvents; BibleView keeps five others.
        val p = "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/session/SessionEventRelay.kt"
        assertContains(p, "windowChanges().subscribe { change ->", "window/active-window changes")
        assertContains(p, "progressChanges.subscribe { change ->", "read status, active cycle, memorization")
        assertContains(p, "readingProgressSettingsChanged.subscribe { onReadingProgressSettingsChanged() }", "reading progress settings")
        assertContains(p, "bookmarkChangeEvents().subscribe { change ->", "bookmark changes: favourites apply on the emitter thread")
        assertContains(p, "documentLoader.favouriteLabels.apply(change)", "favourites change synchronously, before the ordered JS handling")
        assertContains(p, "bookmarkChanges.launch(\"bibleview-bookmarks-${'$'}{window.id}\") { onBookmarkChange(change) }", "ordered JS handling")
        assertContains(p, "aiDocChanges.subscribe {", "AI document pages")
        assertEquals(5, Regex("""subscriptions\.add\(""").findAll(src(p)).count(), "five subscriptions moved")
        assertTrue(!src(p).contains("subscribeOnMain"), "the old subscribers ran on the emitter thread")
        val view = src("android/view/activity/page/BibleView.kt")
        assertEquals(5, Regex("""subscriptions\.add\(""").findAll(view).count(), "BibleView keeps addons, settings, llm, insets, full screen")
        assertContains("android/view/activity/page/BibleView.kt", "eventRelay.start()", "listenEvents starts the relay")
        assertContains("android/view/activity/page/BibleView.kt", "eventRelay.stop()", "listenEvents stops the relay")
    }

    @Test fun aiServicesRegisterConfigRefreshWithApplicationOwner() {
        listOf("AiSettingsServiceImpl", "LlmProviderServiceImpl", "LlmModelServiceImpl", "PromptServiceImpl").forEach {
            assertContains("android/view/activity/ai/$it.kt", "settings.configChanged.collectOwned(appScope) { refresh() }", "synchronous registration with application-owned suspend processing")
        }
    }

    @Test fun promptRepositoryDropsItsCacheOnReload() {
        // Task 14 moved the subscription behind the PromptPackSource port: the repository subscribes to the port...
        assertContains("service/llm/PromptRepository.kt", "promptPacks.reloaded.subscribe { addonPromptsCache = null }", "was on<ReloadAddonsEvent>")
        // ...and the Android port must forward the add-on reload stream.
        assertContains("android/platform/AndroidPromptPackSource.kt", "override val reloaded: Events<Unit> get() = AndBibleAddons.reloaded", "port forwards the add-on reload")
    }

    @Test fun androidMediaSessionSoundsOnForegroundSynchronously() =
        assertContains("android/platform/AndroidMediaSession.kt", "CurrentActivityHolder.appPositionChanges.subscribe {", "was on<AppToBackgroundEvent>, an edge (Review Focus 2)")

    @Test fun aTapCallsTheHostDirectly() =
        assertContains("android/view/activity/page/BibleGestureListener.kt", "mainBibleActivity.onBibleViewTouched()", "replaces the BibleViewTouched post")

    @Test fun everyWorkspaceSettingsWriterNotifies() {
        mapOf(
            // I2 Stream B Task 4 moved one writer (`setLimitAmbiguousModalSize`) into the shared bridge.
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/bridge/ReadingBridge.kt" to 1,
            // Task 5 moved the other one (`setStudyPadCursor`) into the bookmark family.
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/bridge/BookmarkBridgeFamily.kt" to 1,
            // Task 6 moved the last one (`toggleCompareDocument`) into the link family; the adapter has none left.
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/bridge/LinkBridgeFamily.kt" to 1,
            "android/view/activity/bookmark/ManageLabelsContract.kt" to 1,
            "android/control/page/window/WindowRepository.kt" to 1,
            "android/control/bookmark/BookmarkControl.kt" to 2,
        ).forEach { (path, n) ->
            assertEquals(n, Regex("""WorkspaceChanges\.notifySettingsEdited\(\)""").findAll(src(path)).count(), path)
        }
    }
}
