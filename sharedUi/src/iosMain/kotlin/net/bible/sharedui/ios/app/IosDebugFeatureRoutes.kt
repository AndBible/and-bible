package net.bible.sharedui.ios.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import net.bible.sharedui.app.debugSettingsLauncherVisible
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.AppResultChannels
import net.bible.sharedui.app.DebugFeatureOnce
import net.bible.sharedui.app.DebugFeatureScenario
import net.bible.sharedui.assembly.IosDebugLaunchArgs
import net.bible.sharedui.strings.LocalStrings
import org.koin.core.Koin

/** Real graph only: no production reading callbacks, arbitrary routes, or replacement feature UI. */
@Composable
internal fun IosDebugFeatureRoutes(ready: Boolean, nav: NavHostController, channels: AppResultChannels, koin: Koin, hosts: net.bible.sharedui.app.AppHosts, askBookmarks: () -> Unit) {
    val scenario = remember { IosDebugLaunchArgs.featureScenario() } ?: return
    val once = remember { DebugFeatureOnce() }
    LaunchedEffect(ready) {
        if (!once.take(ready && IosReadingReady.isReady, IosDebugLaunchArgs.referenceApplied)) return@LaunchedEffect
        val route = when (scenario) {
            DebugFeatureScenario.CHOOSER -> NavRoutes.chooseDocument("BIBLE")
            DebugFeatureScenario.PLAN_SETTINGS -> NavRoutes.dailyReading()
            DebugFeatureScenario.DOWNLOAD -> NavRoutes.download()
            DebugFeatureScenario.SETTINGS -> NavRoutes.SETTINGS
            DebugFeatureScenario.BOOKMARKS -> {
                // Ask exactly as the drawer's Bookmarks row does, or the reading destination drops the answer as unasked.
                askBookmarks()
                NavRoutes.bookmarks()
            }
            DebugFeatureScenario.LABELS -> {
                val bookmarks = koin.get<BookmarkControl>()
                for ((name, icon) in listOf("Feature star" to "star", "Feature default" to null)) {
                    if (bookmarks.allLabels().none { it.name == name }) {
                        bookmarks.insertOrUpdateLabel(Label(name = name, customIcon = icon, new = true))
                    }
                    check(bookmarks.allLabels().any { it.name == name && it.customIcon == icon }) {
                        "DEBUG label fixture was not persisted: $name"
                    }
                }
                NavRoutes.manageLabels(ManageLabelsContract.ManageLabelsData(ManageLabelsContract.Mode.STUDYPAD).toJSON())
            }
        }
        nav.navigate(route)
    }
    // This DEBUG scenario consumes the document chooser output for the fixture routes; in production the reading destination's
    // ReadingResultCollector (IosReadingResults) consumes the same channel.
    val pending by channels.document.pending.collectAsState()
    LaunchedEffect(pending, ready) {
        net.bible.sharedui.app.consumeDebugDocument(scenario, ready, channels.document::consume) { initials ->
            val book = checkNotNull(SwordDocumentFacade.getDocumentByInitials(initials))
            koin.get<WindowControl>().activeWindowPageManager.setCurrentDocument(book)
        }
    }
    if (!ready) return
    val strings = LocalStrings.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column {
            Text(strings.appName, Modifier.testTag("debug-feature-ready"))
            if (scenario == DebugFeatureScenario.PLAN_SETTINGS) {
                val loaded by hosts.readingPlan.loadedReadingDay.collectAsState()
                val entry by nav.currentBackStackEntryAsState()
                if (debugSettingsLauncherVisible(entry?.destination?.route, loaded != null)) {
                    TextButton(onClick = { nav.navigate(NavRoutes.SETTINGS) }, modifier = Modifier.testTag("debug-open-settings")) { Text(strings.settings) }
                }
                TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.testTag("debug-feature-back")) { Text(strings.settingsEditorBack) }
            }
            if (scenario == DebugFeatureScenario.CHOOSER) {
                TextButton(onClick = { nav.navigate(NavRoutes.chooseDocument("BIBLE")) }, modifier = Modifier.testTag("debug-open-chooser")) { Text(strings.chooseDocumentTitle) }
            }
        }
    }
}
