@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedcore.docs.UserGuideToc
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.CappedBarFontScale
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

/** A row of the "Tutorial videos" list: the episode [title] and the [url] it opens. */
@kotlin.native.HiddenFromObjC
data class VideoRowUi(val title: String, val url: String)

/**
 * The full-screen Help & tips: a "Tips" tab (the tip cards, a link to the full documentation and the version) and a
 * "User guide" tab (the documentation table of contents from [UserGuideToc], with a play button on every page that
 * has a published tutorial episode, and the list of [tutorialVideos]). Stateless apart from the selected tab:
 * every link goes through [onOpenUrl], so the host can confirm before leaving the app.
 *
 * [isPublished] decides which of a page's episodes get a play button; the default is "has a YouTube id" and a test
 * can substitute its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@kotlin.native.HiddenFromObjC
fun HelpScreen(
    tips: List<HelpTipUi>,
    sections: List<UserGuideToc.Section>,
    tutorialVideos: List<VideoRowUi>,
    versionText: String,
    initialTab: Int = 0,
    onOpenUrl: (String) -> Unit,
    onUp: () -> Unit,
    isPublished: (TutorialVideos.Episode) -> Boolean = { it.youtubeId != null },
) {
    val strings = LocalStrings.current
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    AbScaffold(title = strings.helpAndTips, onNavigateUp = onUp) { padding ->
        Column(Modifier.padding(padding)) {
            CappedBarFontScale {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(strings.helpTabTips) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(strings.helpTabUserGuide) })
                }
            }
            if (tab == 0) {
                TipsTab(tips, versionText, onOpenUrl)
            } else {
                UserGuideTab(sections, tutorialVideos, onOpenUrl, isPublished)
            }
        }
    }
}

@Composable
private fun TipsTab(tips: List<HelpTipUi>, versionText: String, onOpenUrl: (String) -> Unit) {
    val strings = LocalStrings.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
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
                Text(versionText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@Composable
private fun UserGuideTab(
    sections: List<UserGuideToc.Section>,
    tutorialVideos: List<VideoRowUi>,
    onOpenUrl: (String) -> Unit,
    isPublished: (TutorialVideos.Episode) -> Boolean,
) {
    val strings = LocalStrings.current
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Text(
                strings.helpUserGuideIntro,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        item { GuideRow(strings.helpUserGuideHome, { onOpenUrl(DocsLinks.BASE_URL) }) }
        for (section in sections) {
            item(key = "h:${section.title}") { SectionHeader(section.title) }
            items(section.pages, key = { "p:${it.stem}" }) { page ->
                GuideRow(page.title, { onOpenUrl(page.url) }) {
                    // One play button per published episode, each named by its episode title.
                    page.episodes.filter(isPublished).forEach { episode ->
                        val title = UserGuideToc.episodeTitles[episode] ?: episode.slug
                        IconButton({ onOpenUrl(TutorialVideos.url(episode)) }) {
                            Icon(Icons.Outlined.PlayCircle, contentDescription = strings.helpPlayVideo(title))
                        }
                    }
                }
            }
        }
        if (tutorialVideos.isNotEmpty()) {
            item(key = "h:videos") { SectionHeader(strings.helpTutorialVideos) }
            items(tutorialVideos, key = { "v:${it.url}" }) { video ->
                GuideRow(video.title, { onOpenUrl(video.url) })
            }
        }
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
private fun GuideRow(title: String, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = trailing?.let { t -> { Row { t() } } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
