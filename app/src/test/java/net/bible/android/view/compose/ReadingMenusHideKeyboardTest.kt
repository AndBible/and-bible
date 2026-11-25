package net.bible.android.view.compose

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.cancel
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.android.view.activity.page.screen.PaneMenuAnchor
import net.bible.sharedui.theme.AbTheme
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Menus must explicitly hide a native editor's IME, without relying on popup focus/OEM behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = TestBibleApplication::class)
class ReadingMenusHideKeyboardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var repository: WindowRepository
    private lateinit var host: NavHostComposeActivity
    private lateinit var reading: ComposeReadingViewHost
    private lateinit var container: ActivityController<Activity>
    private lateinit var editor: EditText
    private lateinit var ime: InputMethodManager

    @Before fun setup() {
        repository = net.bible.test.testWindowRepository()
        CommonUtils.windowControl.windowRepository = repository
        repository.initializeAtEdge()
        DatabaseResetter.runResetBlocking {
            GlobalContext.get().get<net.bible.service.cloudsync.documents.DocumentSyncSettings>().ensureLoaded()
        }
        host = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        host.readingAppBootstrap.windowRepository = repository
        host.setNewHistoryTraversal(GlobalContext.get().get())
        CurrentActivityHolder.activate(host)
        reading = net.bible.test.fixtureReadingHost(host)
        host.composeReadingViewHost = reading
        // Attach this uncreated host's decor to a plain activity: real focus/token/IMM, no startup graph.
        container = Robolectric.buildActivity(Activity::class.java).setup()
        editor = EditText(host)
        host.window.setContentView(editor)
        container.get().setContentView(host.window.decorView)
        editor.requestFocus()
        // Activity is deliberately not created; its shadow does not resolve decor focus automatically.
        shadowOf(host).setCurrentFocus(editor)
        ime = host.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        assertNotNull("native editor has an attached window token", editor.windowToken)
        assertTrue("host resolves the native editor rather than a Compose field", host.currentFocus === editor)
        ime.showSoftInput(editor, 0)
        assertTrue("precondition: keyboard shown for native editor", shadowOf(ime).isSoftInputVisible)
    }

    @After fun cleanup() {
        if (::container.isInitialized) {
            (container.get().findViewById<View>(android.R.id.content) as ViewGroup).removeAllViews()
            container.pause().stop().destroy()
        }
        if (::host.isInitialized) {
            host.composeReadingViewHost?.dispose()
            host.lifecycleScope.cancel()
            CurrentActivityHolder.deactivate(host)
        }
        if (::repository.isInitialized) DatabaseResetter.resetDatabase(repository.scope)
    }

    @Test fun openingPaneMenuHidesNativeEditorKeyboard() {
        compose.setContent {
            AbTheme {
                Column {
                    TextButton(onClick = { reading.hostState.openPaneMenu(repository.activeWindow.id.toString(), PaneMenuAnchor.Pane) }) {
                        Text("Open pane menu")
                    }
                }
            }
        }
        compose.onNodeWithText("Open pane menu").performClick()
        compose.runOnIdle {
            assertNotNull("the real pane menu state opens", reading.hostState.paneMenuWindowId.value)
            assertFalse("opening pane menu hides native editor IME", shadowOf(ime).isSoftInputVisible)
        }
    }

    @Test fun openingOverflowMenuHidesNativeEditorKeyboard() {
        compose.setContent {
            AbTheme {
                TextButton(onClick = reading::openOverflowMenu) { Text("Open overflow menu") }
            }
        }
        compose.onNodeWithText("Open overflow menu").performClick()
        compose.runOnIdle {
            assertTrue("the real overflow state opens", reading.overflowExpandedForTest)
            assertFalse("opening overflow hides native editor IME", shadowOf(ime).isSoftInputVisible)
        }
    }
}
