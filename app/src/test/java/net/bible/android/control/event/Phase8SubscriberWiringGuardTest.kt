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
    private fun src(path: String) = File("src/main/java/net/bible/$path").readText()

    private fun assertContains(path: String, needle: String, why: String) =
        assertTrue(src(path).contains(needle), "$path must contain `$needle`: $why")

    @Test fun bibleViewListensToAddonsWorkspaceSettingsAndAiConfig() {
        val p = "android/view/activity/page/BibleView.kt"
        assertContains(p, "AndBibleAddons.reloaded.subscribe {", "reload_addons, synchronous like the old on{}")
        assertContains(p, "WorkspaceChanges.changes.subscribe {", "family (a) settings reach updateConfig")
        assertContains(p, "AiSettings.configChanged.subscribe { updateConfig() }", "Review Focus 3: llmConfigured")
    }

    @Test fun aiServicesRefreshOnMainOnConfigChange() {
        listOf("AiSettingsServiceImpl", "LlmProviderServiceImpl", "LlmModelServiceImpl", "PromptServiceImpl").forEach {
            assertContains("android/view/activity/ai/$it.kt", "AiSettings.configChanged.subscribeOnMain { refresh() }", "was onMain<AppSettingsUpdated>")
        }
    }

    @Test fun promptRepositoryDropsItsCacheOnReload() =
        assertContains("service/llm/PromptRepository.kt", "AndBibleAddons.reloaded.subscribe { addonPromptsCache = null }", "was on<ReloadAddonsEvent>")

    @Test fun mediaButtonHandlerSoundsOnForegroundSynchronously() =
        assertContains("service/device/speak/MediaButtonHandler.kt", "CurrentActivityHolder.appPositionChanges.subscribe {", "was on<AppToBackgroundEvent>, an edge (Review Focus 2)")

    @Test fun aTapCallsTheHostDirectly() =
        assertContains("android/view/activity/page/BibleGestureListener.kt", "mainBibleActivity.onBibleViewTouched()", "replaces the BibleViewTouched post")

    @Test fun everyWorkspaceSettingsWriterNotifies() {
        mapOf(
            "android/view/activity/page/BibleJavascriptInterface.kt" to 3,
            "android/view/activity/bookmark/ManageLabelsContract.kt" to 1,
            "android/control/page/window/WindowRepository.kt" to 1,
            "android/control/bookmark/BookmarkControl.kt" to 2,
        ).forEach { (path, n) ->
            assertEquals(n, Regex("""WorkspaceChanges\.notifySettingsEdited\(\)""").findAll(src(path)).count(), path)
        }
    }
}
