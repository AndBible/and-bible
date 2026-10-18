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
            "android/view/activity/page/BibleJavascriptInterface.kt" to 1,
            // I2 Stream B Task 4 moved one writer (`setLimitAmbiguousModalSize`) into the shared bridge.
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/bridge/ReadingBridge.kt" to 1,
            // Task 5 moved the other one (`setStudyPadCursor`) into the bookmark family.
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/bridge/BookmarkBridgeFamily.kt" to 1,
            "android/view/activity/bookmark/ManageLabelsContract.kt" to 1,
            "android/control/page/window/WindowRepository.kt" to 1,
            "android/control/bookmark/BookmarkControl.kt" to 2,
        ).forEach { (path, n) ->
            assertEquals(n, Regex("""WorkspaceChanges\.notifySettingsEdited\(\)""").findAll(src(path)).count(), path)
        }
    }
}
