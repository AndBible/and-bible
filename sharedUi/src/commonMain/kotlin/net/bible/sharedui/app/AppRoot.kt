package net.bible.sharedui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import net.bible.sharedui.ai.nav.AiNavDeps
import net.bible.sharedui.ai.nav.aiNavGraph
import net.bible.sharedui.backup.nav.BackupNavDeps
import net.bible.sharedui.backup.nav.backupNavGraph
import net.bible.sharedui.bookmark.nav.BookmarkNavDeps
import net.bible.sharedui.bookmark.nav.bookmarkNavGraph
import net.bible.sharedui.download.nav.DownloadNavDeps
import net.bible.sharedui.download.nav.downloadNavGraph
import net.bible.sharedui.installzip.nav.InstallZipNavDeps
import net.bible.sharedui.installzip.nav.installZipNavGraph
import net.bible.sharedui.mydocuments.nav.MyDocumentsNavDeps
import net.bible.sharedui.mydocuments.nav.myDocumentsNavGraph
import net.bible.sharedui.navigation.nav.ChooserNavDeps
import net.bible.sharedui.navigation.nav.chooserNavGraph
import net.bible.sharedui.readingplan.nav.ReadingPlanNavDeps
import net.bible.sharedui.readingplan.nav.readingPlanNavGraph
import net.bible.sharedui.search.nav.SearchNavDeps
import net.bible.sharedui.search.nav.searchNavGraph
import net.bible.sharedui.settings.nav.SettingsNavDeps
import net.bible.sharedui.settings.nav.settingsNavGraph
import net.bible.sharedui.startup.nav.WelcomeNavDeps
import net.bible.sharedui.startup.nav.welcomeNavGraph
import net.bible.sharedui.workspaces.nav.WorkspaceNavDeps
import net.bible.sharedui.workspaces.nav.workspaceNavGraph

/** The deps of every non-reading feature graph that [AppRoot] installs; [AppHosts.graphs] builds it from the 13 feature hosts (incl. cloudDocuments). */
class AppGraphs(
    val ai: AiNavDeps,
    val bookmark: BookmarkNavDeps,
    val readingPlan: ReadingPlanNavDeps,
    val search: SearchNavDeps,
    val settings: SettingsNavDeps,
    val download: DownloadNavDeps,
    val myDocuments: MyDocumentsNavDeps,
    val chooser: ChooserNavDeps,
    val workspace: WorkspaceNavDeps,
    val backup: BackupNavDeps,
    val installZip: InstallZipNavDeps,
    val welcome: WelcomeNavDeps,
)

/**
 * The shared app root: the single `NavHost` with every feature graph, plus the host-level [overlays]
 * (collectors and sheets that must outlive any one destination) composed beside it.
 *
 * [readingGraph] is a slot: the reading destination is still wired by the platform host.
 * Theme, composition locals and the destination listener stay with the platform host.
 */
@Composable
fun AppRoot(
    navController: NavHostController,
    startRoute: String,
    graphs: AppGraphs,
    readingGraph: NavGraphBuilder.() -> Unit,
    overlays: @Composable () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
        // Paint an opaque themed ground BEHIND the graph. navigation-compose's default
        // transition is a crossfade, and while both screens are semi-transparent the
        // window background shows through — which on a dark or e-ink theme reads as a
        // flash of the wrong colour on every navigation. AMR hit exactly this.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        aiNavGraph(navController, graphs.ai)
        bookmarkNavGraph(navController, graphs.bookmark)
        readingPlanNavGraph(navController, graphs.readingPlan)
        searchNavGraph(navController, graphs.search)
        settingsNavGraph(navController, graphs.settings)
        downloadNavGraph(navController, graphs.download)
        myDocumentsNavGraph(navController, graphs.myDocuments)
        chooserNavGraph(navController, graphs.chooser)
        workspaceNavGraph(navController, graphs.workspace)
        backupNavGraph(navController, graphs.backup)
        installZipNavGraph(navController, graphs.installZip)
        welcomeNavGraph(graphs.welcome)
        readingGraph()
    }
    overlays()
}
