package net.bible.sharedui.app.chooser

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.sharedcore.navigation.BookPick
import net.bible.sharedcore.navigation.ChapterPick
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.navigation.nav.RefreshGridLanguage
import net.bible.sharedui.settings.nav.LocalLiveSettingsLanguage
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LiveGridLanguageTest {
    @Test fun retainedGridRelocalizesWithoutPickingOrResettingItsDraft() = runComposeUiTest {
        var language by mutableStateOf("en")
        var book = 0
        var chapter = 1
        var result = ""
        var writes = 0
        val options = GridOptions(true, false, true, false, false, false)
        val controller = GridChoosePassageController(CoroutineScope(Dispatchers.Unconfined), options,
            { step, _ -> GridUi(step, iosStrings(language).choosePassageBookName, 1, false, false, false, emptyList()) },
            { writes++ }, { book = it; BookPick.GoChapter }, { chapter = it; ChapterPick.GoVerse },
            { "$book.$chapter.$it" }, { result = it })
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings(language), LocalLiveSettingsLanguage provides true) {
                RefreshGridLanguage(controller)
            }
        }
        waitForIdle()
        language = "fi"
        waitForIdle()
        assertEquals(iosStrings("fi").choosePassageBookName, controller.ui.value?.title)
        assertEquals(GridStep.BOOK, controller.step.value)
        controller.pick(43)
        controller.pick(3)
        language = "en"
        waitForIdle()
        assertEquals(iosStrings("en").choosePassageBookName, controller.ui.value?.title)
        assertEquals(GridStep.VERSE, controller.step.value)
        assertEquals(options, controller.options.value)
        assertEquals(0, writes)
        controller.pick(16)
        assertEquals("43.3.16", result)
        assertTrue(controller.back())
        assertEquals(GridStep.CHAPTER, controller.step.value)
        assertTrue(controller.back())
        assertFalse(controller.back())
    }

    @Test fun androidDoesNotOptIntoCompositionRefresh() = runComposeUiTest {
        var language by mutableStateOf("en")
        val controller = GridChoosePassageController(CoroutineScope(Dispatchers.Unconfined), GridOptions(true, false, true, false, false, false),
            { step, _ -> GridUi(step, iosStrings(language).choosePassageBookName, 1, false, false, false, emptyList()) },
            {}, { BookPick.GoChapter }, { ChapterPick.GoVerse }, { "" }, {})
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings(language)) { RefreshGridLanguage(controller) }
        }
        waitForIdle()
        language = "fi"
        waitForIdle()
        assertEquals(iosStrings("en").choosePassageBookName, controller.ui.value?.title)
    }
}
