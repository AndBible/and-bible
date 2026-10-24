package net.bible.sharedui.app.workspace

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.nav.TextSettingsResult
import net.bible.sharedcore.nav.WorkspaceResult
import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.app.ports.ImagePicker
import net.bible.sharedui.app.ports.TextDisplayEntry
import net.bible.sharedui.app.ports.TextDisplayPlatform
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.iosStrings
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkspaceHostTest {
    private val calls = mutableListOf<String>()
    private val scope = TestScope(StandardTestDispatcher())

    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = scope
        override fun exitHost() { calls += "exit" }
        override fun setWindowTitle(title: String) { calls += "title:$title" }
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    private val strings: Strings = iosStrings("en")
    private var picks = 0
    private var pickerResult: String? = null
    private val imagePicker = object : ImagePicker {
        override suspend fun pickImage(): String? { picks++; return "content://photo/1" }
    }
    private val helpDialogs = object : HelpDialogs {
        override fun show(topic: HelpTopic) { calls += "help:$topic" }
    }

    private val colors = ColorsSnapshot(
        title = "t", dayTextColor = 0, dayBackground = 0, dayNoise = 0, nightTextColor = 0, nightBackground = 0,
        nightNoise = 0, workspaceColor = 0, workspaceColorVisible = true, dayBackgroundImageInitials = null,
        dayBackgroundImageName = "", dayBackgroundImageOpacity = 0, nightBackgroundImageInitials = null,
        nightBackgroundImageName = "", nightBackgroundImageOpacity = 0, inheritedFrom = InheritedFrom.NONE,
    )

    /**
     * A [TextDisplaySettingsService] whose `importBackgroundImage` hands the picker to the caller's contract, as the
     * real one does; every other member is unused by these tests.
     */
    private val unused: TextDisplaySettingsService = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(TextDisplaySettingsService::class.java),
    ) { _, method, _ -> error("unexpected call: ${method.name}") } as TextDisplaySettingsService

    private val service: TextDisplaySettingsService = object : TextDisplaySettingsService by unused {
        override fun loadColors(scope: SettingsScope) = colors
        override fun loadBackgroundOptions(): List<BackgroundImageOption> = emptyList()
        override suspend fun importBackgroundImage(picker: suspend () -> String?): BackgroundImageOption? {
            pickerResult = picker()
            return null
        }
    }

    private val platform = object : TextDisplayPlatform {
        override fun entryFor(args: TextDisplaySettingsArgs) = TextDisplayEntry(
            service = service,
            scope = SettingsScope.Global,
            hideLabelsPayload = { "payload" },
            resultOnLeave = { null },
        )
        override fun applyHideLabelsResult(resultJson: String, controller: TextDisplaySettingsController) = Unit
        override fun thumbnail(token: String): ImageBitmap? = null
    }

    private fun host() = WorkspaceHost(
        shell = shell,
        strings = strings,
        imagePicker = imagePicker,
        helpDialogs = helpDialogs,
        platform = platform,
        workspaceServiceProvider = { error("constructing or building deps must not resolve the workspace service") },
        appScopeProvider = { error("appScope is only needed once the selector controller is built") },
        activeWorkspaceId = { "ws-1" },
    )

    @Test
    fun imagePickGoesThroughPort() = scope.runTest {
        val host = host()
        val session = host.textDisplaySessionFor(TextDisplaySettingsArgs())
        val controller = session.colorControllerFor(SettingsScope.Global)
        controller.onImportBackgroundImage()
        advanceUntilIdle()
        assertEquals(1, picks)
        assertEquals("content://photo/1", pickerResult)
    }

    @Test
    fun navDepsRouteThroughPortsWithoutTouchingServices() = runComposeUiTest {
        val host = host()
        val channel = NavResultChannel<ManageLabelsResult> { error("unused") }
        val workspaceResults = NavResultChannel<WorkspaceResult> { error("unused") }
        val textSettingsResults = NavResultChannel<TextSettingsResult> { error("unused") }
        lateinit var deps: net.bible.sharedui.workspaces.nav.WorkspaceNavDeps
        setContent { deps = host.navDeps(rememberNavController(), workspaceResults, textSettingsResults, channel) }
        // Building the deps resolved neither the workspace service nor the app scope (the providers throw).
        assertSame(channel, deps.manageLabelsResults)
        assertSame(workspaceResults, deps.workspaceResults)
        assertSame(textSettingsResults, deps.textSettingsResults)
        assertEquals(strings.workspaceSelectorTitle, deps.workspaceSelector.title)
        assertEquals("ws-1", deps.textDisplaySettings.activeWorkspaceId())
        deps.workspaceSelector.onHelp()
        deps.exitHost()
        deps.setWindowTitle("x")
        assertEquals(listOf("help:WORKSPACES", "exit", "title:x"), calls)
        assertEquals(null, deps.textDisplaySettings.thumbnailFor("tok"))
    }
}
