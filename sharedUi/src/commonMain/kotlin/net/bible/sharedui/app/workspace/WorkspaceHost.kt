package net.bible.sharedui.app.workspace

import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.database.SettingsBundle
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.nav.TextSettingsResult
import net.bible.sharedcore.nav.WorkspaceResult
import net.bible.sharedcore.settings.ColorSettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.workspaces.WorkspaceSelectorController
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.app.ports.ImagePicker
import net.bible.sharedui.app.ports.TextDisplayPlatform
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.NavSessionMemo
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.workspaces.nav.TextDisplaySettingsDeps
import net.bible.sharedui.workspaces.nav.TextDisplaySettingsNavState
import net.bible.sharedui.workspaces.nav.TextDisplaySettingsSession
import net.bible.sharedui.workspaces.nav.WorkspaceNavDeps
import net.bible.sharedui.workspaces.nav.WorkspaceSelectorDeps

/**
 * The workspace cluster's deps builder and host baggage: the workspace selector and the text display settings
 * destination (reached from the selector and from the reading view's options).
 *
 * Every result here is delivered in-graph. The two result channels are passed to [navDeps] by the activity (the
 * reading destination's collector reads `workspaceResults` there); their `exitWithResult` stays a hard `error(...)`,
 * the watchdog for an entry with no parent. Every service sits behind a provider, so constructing the host touches
 * no database.
 */
class WorkspaceHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val imagePicker: ImagePicker,
    private val helpDialogs: HelpDialogs,
    private val platform: TextDisplayPlatform,
    private val workspaceServiceProvider: () -> WorkspaceService,
    /** The app-lifetime scope; saves and clones started by the selector run in it (see [selectorControllerFor]). */
    private val appScopeProvider: () -> CoroutineScope,
    private val activeWorkspaceId: () -> String,
) {
    private val workspaceService: WorkspaceService by lazy(workspaceServiceProvider)

    /**
     * The text-display-settings visit's state, held for the HOST's lifetime rather than the back-stack entry's
     * composition. See `TextDisplaySettingsDeps.navStateMemo`. It holds live controllers, so it is NOT
     * serializable into `AppHostsState`: it keeps its host-instance lifetime, as it had in the activity.
     * Dropped by the arm on every exit.
     */
    val textDisplayNavStateMemo = NavSessionMemo<TextDisplaySettingsArgs, TextDisplaySettingsNavState>()

    // Classic's four label bundles: ~90 strings between them, so they stay lazy, and are handed to the graph as
    // `() -> Labels` slots (a plain value would be read where the deps are constructed, on every host launch).
    private val controllerLabels by lazy { strings.buildTextDisplayControllerLabels() }
    private val screenLabels by lazy { strings.buildTextDisplayScreenLabels() }
    private val colorSettingsLabels by lazy { strings.buildColorSettingsLabels() }
    private val backgroundImageChooserLabels by lazy { strings.buildBackgroundImageChooserLabels() }

    /**
     * The selector's memoised controller: host-held rather than built per back-stack entry, because it
     * navigates to the settings editor and navigation-compose disposes its arm's composition while that child is
     * on top. Dropped at every exit so a re-entry is seeded afresh.
     */
    private var selectorController: WorkspaceSelectorController? = null

    /**
     * Classic `WorkspaceSelectorComposeActivity`'s controller plus its `onCreate` side effects. Both side effects
     * run HERE, at construction, once per memoised controller: a `LaunchedEffect` in the arm would re-run on every
     * return from the settings editor and `load()` would wipe the unsaved working set.
     *
     * **The memo is assigned LAST**, so a side effect that throws leaves it untouched and the next entry starts
     * over rather than reusing a half-initialised controller.
     */
    private fun selectorControllerFor(
        onResult: (workspaceId: String?, changed: Boolean) -> Unit,
        onCancel: () -> Unit,
        onEditSettings: (workspaceId: String) -> Unit,
    ): WorkspaceSelectorController {
        selectorController?.let { return it }
        val controller = WorkspaceSelectorController(
            service = workspaceService,
            scope = shell.hostScope,
            // Saves and clones must outlive this host (a dark-mode flip recreates it mid-flush); the Main dispatcher
            // keeps the controller's staging state on the UI thread.
            writeScope = CoroutineScope(appScopeProvider().coroutineContext + Dispatchers.Main),
            onResult = { workspaceId, changed ->
                selectorController = null
                onResult(workspaceId, changed)
            },
            onCancel = {
                selectorController = null
                onCancel()
            },
            onEditSettings = onEditSettings,
        )
        workspaceService.saveCurrentIntoDb()
        controller.load()
        selectorController = controller
        return controller
    }

    /**
     * Called when the host instance is destroyed. A non-null memo means the visit ended without a result (every
     * real exit nulls it first): `WorkspaceSelectorController.clone` inserts the clone into the database at once
     * and stages it only here, so without this a recreation mid-visit (dark-mode flip) leaves a permanent
     * workspace nothing tracks. `discardCreated` rather than `cancel()`, whose exit is a pop on a host that is
     * already being destroyed.
     */
    fun onHostDestroy() {
        selectorController?.discardCreated()
        selectorController = null
    }

    /**
     * Everything one entry into the text-display-settings destination needs, resolved from the route's own
     * arguments -- classic's three coupled `by lazy` fields as one object. The platform decides which service
     * the visit edits: a DETACHED copy for a selector-originated edit (it must touch neither the active workspace
     * nor the shared service), the shared one otherwise.
     */
    internal fun textDisplaySessionFor(args: TextDisplaySettingsArgs): TextDisplaySettingsSession {
        val entry = platform.entryFor(args)
        val service = entry.service
        return TextDisplaySettingsSession(
            initialScope = entry.scope,
            controllerFor = { scope, onNavigate ->
                TextDisplaySettingsController(
                    service = service,
                    settingsScope = scope,
                    labels = controllerLabels,
                    onNavigateCallback = onNavigate,
                )
            },
            colorControllerFor = { scope ->
                ColorSettingsController(
                    service = service,
                    scope = scope,
                    coroutineScope = shell.hostScope,
                    imagePicker = { imagePicker.pickImage() },
                )
            },
            hideLabelsPayload = { scope -> entry.hideLabelsPayload(scope) },
            applyHideLabelsResult = { json, controller -> platform.applyHideLabelsResult(json, controller) },
            resultOnLeave = entry.resultOnLeave,
        )
    }

    /**
     * The graph's workspace deps. [manageLabelsResults] is the SAME instance `BookmarkNavDeps` gets: the label
     * manager is registered by the bookmark graph, and the text-settings destination's Hide-labels row navigates
     * to it in-graph and collects the answer here.
     */
    @Suppress("UNUSED_PARAMETER")
    fun navDeps(
        navController: NavHostController,
        workspaceResults: NavResultChannel<WorkspaceResult>,
        textSettingsResults: NavResultChannel<TextSettingsResult>,
        manageLabelsResults: NavResultChannel<ManageLabelsResult>,
    ): WorkspaceNavDeps = WorkspaceNavDeps(
        exitHost = { shell.exitHost() },
        setWindowTitle = { title -> shell.setWindowTitle(title) },
        workspaceResults = workspaceResults,
        textSettingsResults = textSettingsResults,
        manageLabelsResults = manageLabelsResults,
        workspaceSelector = WorkspaceSelectorDeps(
            controllerFor = { onResult, onCancel, onEditSettings ->
                selectorControllerFor(onResult, onCancel, onEditSettings)
            },
            // Classic's manifest android:label AND the string the screen draws.
            title = strings.workspaceSelectorTitle,
            settingsBundleJson = { id -> workspaceService.settingsBundleJson(id) },
            workspaceIdOf = { json -> SettingsBundle.fromJson(json).workspaceId.toString() },
            onHelp = { helpDialogs.show(HelpTopic.WORKSPACES) },
        ),
        textDisplaySettings = TextDisplaySettingsDeps(
            sessionFor = { args -> textDisplaySessionFor(args) },
            navStateMemo = textDisplayNavStateMemo,
            // Classic's manifest android:label. NOT the screen's own top-bar title, which is scope-dependent and
            // comes from the controller state.
            windowTitle = strings.textDisplaySettingsActivityTitle,
            activeWorkspaceId = activeWorkspaceId,
            // Lambdas, not values: see the lazy label fields above.
            screenLabels = { screenLabels },
            colorSettingsLabels = { colorSettingsLabels },
            backgroundImageChooserLabels = { backgroundImageChooserLabels },
            inheritedFromWorkspace = strings.textOptionsInheritedWorkspace,
            inheritedFromGlobal = strings.textOptionsInheritedGlobal,
            thumbnailFor = { token -> platform.thumbnail(token) },
        ),
    )
}
