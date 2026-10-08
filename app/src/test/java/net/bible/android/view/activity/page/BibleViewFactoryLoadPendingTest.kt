package net.bible.android.view.activity.page

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** F133: a window whose load timed out reloads exactly once when its BibleView is created. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleViewFactoryLoadPendingTest {
    private fun window(): Window {
        val repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        return Window(
            WorkspaceEntities.Window(
                workspaceId = IdType(), isSynchronized = false, isPinMode = false,
                windowLayout = WorkspaceEntities.WindowLayout(WindowState.VISIBLE.toString()),
            ),
            mock(CurrentPageManager::class.java),
            repo,
        )
    }

    @Test
    fun aWindowWithAPendingLoadIsReloadedOnceAndTheFlagCleared() {
        val w = window().apply { loadPending = true }
        var loads = 0
        BibleViewFactory.reloadIfLoadWasDropped(w) { loads++ }
        assertEquals(1, loads)
        assertEquals(false, w.loadPending)
        BibleViewFactory.reloadIfLoadWasDropped(w) { loads++ }
        assertEquals("flag consumed, no second load", 1, loads)
    }

    @Test
    fun aWindowWithoutAPendingLoadIsNotReloaded() {
        var loads = 0
        BibleViewFactory.reloadIfLoadWasDropped(window()) { loads++ }
        assertEquals(0, loads)
    }
}
