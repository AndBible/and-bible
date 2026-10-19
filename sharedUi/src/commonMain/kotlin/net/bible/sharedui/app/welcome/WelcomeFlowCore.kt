@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.sharedcore.platform.AppJson
import net.bible.android.database.SwordDocumentInfo
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.startup.StartupWelcomeController
import net.bible.sharedcore.startup.StartupWelcomeInfo
import net.bible.sharedui.components.AbMultiSelectDialog
import net.bible.sharedui.strings.LocalStrings

/** Shared Welcome state and actions; platform callbacks preserve each host's initialization and navigation. */
@kotlin.native.HiddenFromObjC
class WelcomeFlowCore(
    private val versionText: () -> String,
    private val supportedFormatsText: () -> String,
    private val language: () -> String,
    private val isDiscrete: () -> Boolean,
    private val knownInstalled: suspend () -> List<SwordDocumentInfo>,
    private val megabytesFree: () -> Long,
    private val refuseDownload: () -> Unit,
    private val initialise: suspend () -> Unit,
    private val ensureSyncLoaded: suspend () -> Unit,
    private val navigate: (String) -> Unit,
    private val bookLabel: (SwordDocumentInfo) -> String,
) {
    private var previousInstall = false
    private val controllerLazy = lazy { StartupWelcomeController(::loadInfo) }
    val controller: StartupWelcomeController get() = controllerLazy.value
    val controllerIfCreated: StartupWelcomeController?
        get() = if (controllerLazy.isInitialized()) controllerLazy.value else null
    var redownloadBooks by mutableStateOf<List<SwordDocumentInfo>?>(null)
        private set

    fun loadInfo() = StartupWelcomeInfo(
        versionText(), supportedFormatsText(), previousInstall,
        language().substringBefore('-').substringBefore('_') == "en", !isDiscrete(),
    )

    /** Text-only refresh never creates a controller or probes the database during recomposition. */
    fun refreshTextIfCreated() { controllerIfCreated?.refresh() }

    suspend fun previousInstallDetected(): Boolean = knownInstalled().isNotEmpty()

    suspend fun refreshInfo() {
        previousInstall = previousInstallDetected()
        controller.refresh()
    }

    suspend fun leaveFor(route: String) = initialiseThen { navigate(route) }

    /** Import has no route until the host's picker opens, but has the same prerequisites as Download. */
    suspend fun initialiseThen(action: () -> Unit) {
        initialise()
        ensureSyncLoaded()
        action()
    }

    suspend fun onDownload() {
        // Keep Android's threshold (SharedConstants.REQUIRED_MEGS_FOR_DOWNLOADS) and strict comparison.
        if (megabytesFree() < 50L) {
            refuseDownload()
            return
        }
        leaveFor(NavRoutes.download(firstDownload = true))
    }

    suspend fun onEasyStart() = leaveFor(NavRoutes.download(firstDownload = true, downloadRecommended = true))

    suspend fun onRedownload() {
        redownloadBooks = knownInstalled().sortedBy { it.language }
    }

    suspend fun confirmRedownload(ids: Set<String>) {
        val selected = redownloadBooks.orEmpty().filter { it.initials in ids }
        redownloadBooks = null
        if (selected.isNotEmpty()) leaveFor(NavRoutes.download(firstDownload = true, documentIds = AppJson.encodeToString(selected)))
    }

    @Composable
    fun RedownloadDialog(scope: CoroutineScope) {
        val books = redownloadBooks ?: return
        val strings = LocalStrings.current
        AbMultiSelectDialog(
            title = strings.welcomeRedownloadButton,
            options = books, selectedIds = books.map { it.initials },
            idOf = { it.initials }, labelOf = bookLabel,
            confirmText = strings.okay, dismissText = strings.no,
            onConfirm = { ids -> scope.launch { confirmRedownload(ids.toSet()) } },
            onDismiss = { redownloadBooks = null },
            selectAllText = strings.selectAll, selectNoneText = strings.selectNone,
        )
    }
}
