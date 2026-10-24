package net.bible.android.control.page.window

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    private lateinit var previousPicker: WindowSettingsPicker
    private lateinit var previousRepository: WindowRepository

    @Before fun setUp() {
        windowControl = CommonUtils.windowControl
        previousPicker = windowControl.settingsPicker
        previousRepository = windowControl.windowRepository
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowControl.settingsPicker = WindowSettingsPicker { _, types ->
            offered = types
            listOf(TextDisplaySettings.Types.FONTSIZE)
        }
    }

    @After fun tearDown() {
        try {
            windowControl.settingsPicker = previousPicker
            windowControl.windowRepository = previousRepository
        } finally {
            DatabaseResetter.resetDatabase(windowRepository.scope)
        }
    }

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

    @Test fun copyToGlobalWritesOnlyTheChosenTypeAndPersistsTheRow() {
        val window = windowControl.activeWindow
        window.pageManager.textDisplaySettings.apply { fontSize = 31; showFootNotes = true }
        val before = CommonUtils.globalTextDisplaySettings

        windowControl.copySettingsToGlobal(window)
        idle()

        val after = CommonUtils.globalTextDisplaySettings
        assertEquals(31, after.fontSize)
        assertEquals("not chosen, so unchanged", before.showFootNotes, after.showFootNotes)
        runBlocking { org.koin.java.KoinJavaComponent.get<net.bible.sharedcore.platform.OrderedLauncher>(net.bible.sharedcore.platform.OrderedLauncher::class.java).join(net.bible.service.common.GlobalTextDisplaySettingsStore.KEY) }
        val row = runBlocking { net.bible.service.db.DatabaseContainer.instance.workspaceDb.globalTextDisplaySettingsDao().get() }
        assertEquals(31, row?.textDisplaySettings?.fontSize)
    }
}
