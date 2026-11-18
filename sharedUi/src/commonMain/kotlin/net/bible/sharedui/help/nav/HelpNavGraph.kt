@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.help.nav

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedcore.docs.UserGuideToc
import net.bible.sharedui.help.HelpScreen
import net.bible.sharedui.help.HelpTipUi
import net.bible.sharedui.help.VideoRowUi
import net.bible.sharedui.nav.popOrExitFrom
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.strings.LocalStrings

/**
 * What the Help & tips destination needs from its host. The tips and the version are providers because they depend
 * on the loaded string table; [onOpenUrl] opens a link after the host's confirmation.
 */
@kotlin.native.HiddenFromObjC
class HelpNavDeps(
    val exitHost: () -> Unit,
    val setWindowTitle: (String) -> Unit,
    val tips: () -> List<HelpTipUi>,
    val versionText: () -> String,
    val onOpenUrl: (String) -> Unit,
    /**
     * The app's language tag. A system language without an app translation still drives the captions: the tag is
     * the one the app resolved, which falls back to English, and only a tag with a caption track changes the link.
     */
    val appLanguage: () -> String = { "en" },
    /** The published episodes listed under "Tutorial videos" for an app in the given language; empty while none is uploaded. */
    val tutorialVideos: (appLanguage: String) -> List<VideoRowUi> = { lang ->
        TutorialVideos.published().map {
            VideoRowUi(UserGuideToc.episodeTitles[it] ?: it.slug, TutorialVideos.url(it, lang), TutorialVideos.hasPlainLabel(it, lang))
        }
    },
)

/** The Help & tips destination. */
@kotlin.native.HiddenFromObjC
fun NavGraphBuilder.helpNavGraph(navController: NavHostController, deps: HelpNavDeps) {
    composable(route = NavRoutes.HELP) { entry ->
        val strings = LocalStrings.current
        LaunchedEffect(strings.helpAndTips) { deps.setWindowTitle(strings.helpAndTips) }
        val tips = remember(strings) { deps.tips() }
        val language = remember { deps.appLanguage() }
        val videos = remember { deps.tutorialVideos(language) }
        HelpScreen(
            tips = tips,
            sections = UserGuideToc.sections,
            tutorialVideos = videos,
            appLanguage = language,
            versionText = remember(strings) { deps.versionText() },
            onOpenUrl = deps.onOpenUrl,
            onUp = { navController.popOrExitFrom(entry, deps.exitHost) },
        )
    }
}
