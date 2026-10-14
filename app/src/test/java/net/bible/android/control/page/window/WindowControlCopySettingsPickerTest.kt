package net.bible.android.control.page.window

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The copy-settings commands against a fake [WindowSettingsPicker]: no activity, no dialog. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowControlCopySettingsPickerTest {
    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private var offered: List<TextDisplaySettings.Types>? = null

    @Before fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowControl.settingsPicker = WindowSettingsPicker { _, types ->
            offered = types
            listOf(TextDisplaySettings.Types.FONTSIZE)
        }
    }

    @After fun tearDown() { DatabaseResetter.resetDatabase(windowRepository.scope) }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun copyToWorkspaceCopiesOnlyTheFontSizeThePickerChose() {
        val window = windowControl.activeWindow
        window.pageManager.textDisplaySettings.apply { fontSize = 30; showFootNotes = true }
        windowRepository.textDisplaySettings.apply { fontSize = 10; showFootNotes = false }

        windowControl.copySettingsToWorkspace(window)
        idle()

        assertEquals(TextDisplaySettings.Types.values().toList(), offered)
        assertEquals(30, windowRepository.textDisplaySettings.fontSize)
        assertEquals("not chosen, so not copied", false, windowRepository.textDisplaySettings.showFootNotes)
    }

    @Test fun copyToGlobalWritesTheChosenTypeIntoTheGlobalSnapshot() {
        val window = windowControl.activeWindow
        window.pageManager.textDisplaySettings.fontSize = 31

        windowControl.copySettingsToGlobal(window)
        idle()

        assertEquals(31, CommonUtils.globalTextDisplaySettings.fontSize)
    }
}
