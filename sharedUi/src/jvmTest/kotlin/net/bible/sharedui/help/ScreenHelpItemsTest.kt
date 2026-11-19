package net.bible.sharedui.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchType
import net.bible.sharedui.backup.BackupRestoreScreen
import net.bible.sharedcore.backup.BackupState
import net.bible.sharedui.components.HelpOpener
import net.bible.sharedui.components.LocalHelpOpener
import net.bible.sharedui.readingplan.DailyReadingListScreen
import net.bible.sharedui.readingplan.ReadingPlanSelectorScreen
import net.bible.sharedui.search.SearchScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** #4159: each screen's overflow "Help in the user guide" item opens Help & tips at the screen's own topic. */
@OptIn(ExperimentalTestApi::class)
class ScreenHelpItemsTest {
    private val strings = iosStrings("en")

    private fun topicOpenedFrom(screen: @Composable () -> Unit): List<DocsLink> {
        val topics = mutableListOf<DocsLink>()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalStrings provides strings, LocalHelpOpener provides HelpOpener { topics += it }) {
                    AbTheme(darkTheme = false) { screen() }
                }
            }
            onNodeWithContentDescription(strings.menu).performClick()
            onNodeWithText(strings.helpInUserGuide).performClick()
        }
        return topics
    }

    @Test fun backupOpensBackupAndRestore() = assertEquals(
        listOf(DocsLink.BACKUP_RESTORE),
        topicOpenedFrom { BackupRestoreScreen(BackupState(), { _, _ -> }, {}, {}, {}, {}, {}, {}) },
    )

    @Test fun searchOpensSearch() = assertEquals(
        listOf(DocsLink.SEARCH),
        topicOpenedFrom {
            SearchScreen(
                title = "Search", query = "", searchType = SearchType.ALL_WORDS, bibleSection = SearchBibleSection.ALL,
                availableTranslations = emptyList(), selectedTranslationIds = emptyList(),
                onQueryChange = {}, onSearchType = {}, onBibleSection = {}, onTranslations = {},
                onSubmit = {}, onNavigateUp = {},
            )
        },
    )

    @Test fun readingPlanSelectorOpensReadingPlans() = assertEquals(
        listOf(DocsLink.READING_PLANS),
        topicOpenedFrom { ReadingPlanSelectorScreen("Plans", emptyList(), false, null, {}, {}, {}, {}, {}) },
    )

    @Test fun dailyReadingListOpensReadingPlans() = assertEquals(
        listOf(DocsLink.READING_PLANS),
        topicOpenedFrom { DailyReadingListScreen("Days", emptyList(), null, {}, {}, {}) },
    )
}
