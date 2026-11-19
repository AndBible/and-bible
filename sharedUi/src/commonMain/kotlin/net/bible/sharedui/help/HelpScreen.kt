@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.docs.SiteLinks
import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedcore.docs.UserGuideToc
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.CappedBarFontScale
import net.bible.sharedui.components.volumeScrollTarget
import net.bible.sharedui.strings.keyed.KeyedUserGuideStrings
import net.bible.sharedui.strings.LocalStrings

/** One tip card of the Help & tips screen. [videoUrl] and [docUrl] are null when the tip has no video or manual page. */
@kotlin.native.HiddenFromObjC
data class HelpTipUi(
    val title: String,
    /** Body text; `\n` is a line break. */
    val text: String,
    val videoUrl: String?,
    val videoLabel: String,
    val docUrl: String?,
)

/**
 * A row of the "Tutorial videos" list: the episode [title] and the [url] it opens. [plainLabel] is false when the
 * video has no caption track in the app language, which adds the "(English)" qualifier to the row's label.
 */
@kotlin.native.HiddenFromObjC
data class VideoRowUi(val title: String, val url: String, val plainLabel: Boolean = true)

/**
 * The full-screen Help & tips: a "Tips" tab (the tip cards, a link to the full documentation and the version) and a
 * "User guide" tab (the documentation table of contents from [UserGuideToc], with a playable sibling row after
 * each page for each of its tutorial episodes). The tab's first row opens a sub-view listing [tutorialVideos] (the
 * whole series, numbered, in order; the row is hidden when the list is empty); Up and Back return from it to the tab. Stateless apart from the selected tab
 * and the sub-view, which survive rotation:
 * every link goes through [onOpenUrl], so the host can confirm before leaving the app.
 *
 * All episodes are visible. Unpublished episodes open the placeholder; [TutorialVideos] chooses the URL and
 * label from the opened video's captions for [appLanguage].
 */
/** The Help & tips tabs, in display order. The first entry is the default tab. */
@kotlin.native.HiddenFromObjC
enum class HelpTab { UserGuide, Tips }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@kotlin.native.HiddenFromObjC
fun HelpScreen(
    tips: List<HelpTipUi>,
    sections: List<UserGuideToc.Section>,
    tutorialVideos: List<VideoRowUi>,
    versionText: String,
    guideStrings: KeyedUserGuideStrings = KeyedUserGuideStrings.english(),
    appLanguage: String = "en",
    initialTab: HelpTab = HelpTab.UserGuide,
    initialShowVideos: Boolean = false,
    onOpenUrl: (String) -> Unit,
    onUp: () -> Unit,
) {
    val strings = LocalStrings.current
    // Saved as the ordinal: an enum is not a portable saveable type on every Compose target.
    var tabOrdinal by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    val tab = HelpTab.entries[tabOrdinal]
    var showVideos by rememberSaveable { mutableStateOf(initialShowVideos) }
    val inVideos = showVideos && tab == HelpTab.UserGuide
    PlatformBackHandler(enabled = inVideos) { showVideos = false }
    AbScaffold(
        title = if (inVideos) strings.helpTutorialVideos else strings.helpAndTips,
        onNavigateUp = if (inVideos) ({ showVideos = false }) else onUp,
    ) { padding ->
        if (inVideos) {
            TutorialVideosList(tutorialVideos, onOpenUrl, Modifier.padding(padding))
            return@AbScaffold
        }
        Column(Modifier.padding(padding)) {
            CappedBarFontScale {
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    for (entry in HelpTab.entries) {
                        Tab(
                            selected = tab == entry,
                            onClick = { tabOrdinal = entry.ordinal },
                            text = { Text(if (entry == HelpTab.Tips) strings.helpTabTips else strings.helpTabUserGuide) },
                        )
                    }
                }
            }
            when (tab) {
                HelpTab.UserGuide -> UserGuideTab(
                    sections, guideStrings, appLanguage, onOpenUrl,
                    onWatchInOrder = if (tutorialVideos.isEmpty()) null else ({ showVideos = true }),
                )
                HelpTab.Tips -> TipsTab(tips, versionText, onOpenUrl)
            }
        }
    }
}

@Composable
private fun TipsTab(tips: List<HelpTipUi>, versionText: String, onOpenUrl: (String) -> Unit) {
    val strings = LocalStrings.current
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().volumeScrollTarget(listState),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tips) { tip ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(tip.title, style = MaterialTheme.typography.titleMedium)
                    Text(tip.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    if (tip.videoUrl != null || tip.docUrl != null) {
                        // Stacked, not side by side: translated labels are long and a Row squeezed the second one.
                        Column {
                            tip.videoUrl?.let { TextButton({ onOpenUrl(it) }) { Text(tip.videoLabel) } }
                            tip.docUrl?.let { TextButton({ onOpenUrl(it) }) { Text(strings.helpReadMoreLink) } }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                TextButton({ onOpenUrl(DocsLinks.BASE_URL) }) { Text(strings.helpFullDocumentationLink) }
                // The sponsor line the old Android Help notice carried.
                TextButton({ onOpenUrl(SiteLinks.SHOP_URL) }) { Text("${strings.buyDevelopment2}: ${strings.buyDevelopment}") }
                Text(versionText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@Composable
private fun UserGuideTab(
    sections: List<UserGuideToc.Section>,
    guideStrings: KeyedUserGuideStrings,
    appLanguage: String,
    onOpenUrl: (String) -> Unit,
    /** Opens the in-order series sub-view; null hides the row (no catalogued videos). */
    onWatchInOrder: (() -> Unit)?,
) {
    val strings = LocalStrings.current
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().volumeScrollTarget(listState)) {
        item {
            Text(
                strings.helpUserGuideIntro,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (onWatchInOrder != null) item { WatchInOrderRow(onWatchInOrder) }
        item { GuideRow(strings.helpUserGuideHome, { onOpenUrl(DocsLinks.BASE_URL) }) }
        for (section in sections) {
            item(key = "h:${section.title}") { SectionHeader(guideStrings.section(section)) }
            for (page in section.pages) {
                item(key = "p:${page.stem}") { GuideRow(guideStrings.page(page), { onOpenUrl(page.url) }) }
                // Separate rows keep multiple episodes usable on a phone.
                items(page.episodes, key = { "e:${page.stem}:${it.name}" }) { episode ->
                    VideoRow(
                        guideStrings.episode(episode),
                        TutorialVideos.hasPlainLabel(episode, appLanguage),
                        { onOpenUrl(TutorialVideos.url(episode, appLanguage)) },
                    )
                }
            }
        }
    }
}

/** The sub-view of the User guide tab: the whole tutorial series in order. */
@Composable
private fun TutorialVideosList(videos: List<VideoRowUi>, onOpenUrl: (String) -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth().volumeScrollTarget(listState)) {
        // Several episodes can share the placeholder URL, so it cannot be a LazyColumn key.
        items(videos) { video -> VideoRow(video.title, video.plainLabel, { onOpenUrl(video.url) }) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        HorizontalDivider()
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun GuideRow(title: String, onClick: () -> Unit) {
    val strings = LocalStrings.current
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(strings.helpReadInUserGuide) },
        leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = strings.helpReadPage(title), onClick = onClick),
    )
}

/** A playable video row: play icon, the [title] and the "Watch tutorial video" label ("(English)" unless [plainLabel]). */
@Composable
private fun VideoRow(title: String, plainLabel: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(if (plainLabel) strings.watchTutorialVideoPlain else strings.watchTutorialVideo) },
        leadingContent = { Icon(Icons.Outlined.PlayCircle, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = strings.helpPlayVideo(title), onClick = onClick),
    )
}

/** The first row of the User guide tab: opens the in-order list of all tutorial videos. */
@Composable
private fun WatchInOrderRow(onClick: () -> Unit) {
    val strings = LocalStrings.current
    ListItem(
        headlineContent = { Text(strings.helpWatchInOrder) },
        leadingContent = { Icon(Icons.Outlined.PlayCircle, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = strings.helpWatchInOrder, onClick = onClick),
    )
}
