package net.bible.android.control.page

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class GlobalTextDisplaySettingsSourceTest {
    @Test fun nonSpecificSettingsUseInjectedGlobalRow() {
        val original = GlobalContext.get().get<GlobalTextDisplaySettingsSource>()
        val source = object : GlobalTextDisplaySettingsSource {
            override val current = TextDisplaySettings(fontSize = 21)
        }
        val control = mock(WindowControl::class.java)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        val repository = WindowRepository(scope)
        repository.textDisplaySettings = TextDisplaySettings()
        `when`(control.windowRepository).thenReturn(repository)
        val manager = CurrentPageManager(mock(BibleTraverser::class.java), mock(BookmarkControl::class.java), control)
        loadKoinModules(module { single<GlobalTextDisplaySettingsSource> { source } })
        try {
            assertEquals(21, manager.actualTextDisplaySettings.fontSize)
            source.current.fontSize = 25
            assertEquals(25, manager.actualTextDisplaySettings.fontSize)
            manager.textDisplaySettings.fontSize = 18
            assertEquals(18, manager.actualTextDisplaySettings.fontSize)
        } finally {
            manager.destroy()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            loadKoinModules(module { single<GlobalTextDisplaySettingsSource> { original } })
        }
    }
}
