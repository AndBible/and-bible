package net.bible.sharedui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.view.activity.settings.DetachedWorkspaceEdit
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.service.sword.ContentSource
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedlogic.settings.PrefItemHandle
import net.bible.sharedlogic.settings.TextDisplayArray
import net.bible.sharedlogic.settings.TextDisplayPrefItems
import net.bible.sharedlogic.settings.TextDisplayString
import net.bible.sharedui.app.workspace.buildTextDisplayScreenLabels
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedui.strings.keyed.textDisplayStringKey
import net.bible.sharedui.strings.lookup
import net.bible.sharedui.strings.lookupArray
import net.bible.sharedui.strings.lookupPlural
import net.bible.sharedui.theme.AbTheme
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Uses the real service's numeric rows so unit formatting and the editor wiring are tested together. */
@OptIn(ExperimentalTestApi::class)
class NumericSliderContentTest {
    private val strings = iosStrings("en")
    private val table = StringTable.of(
        "en", { lookup("en", it) }, { n, count -> lookupPlural("en", n, count) }, { lookupArray("en", it) },
    )
    private val prefItems = object : TextDisplayPrefItems {
        override fun item(bundle: SettingsBundle, type: TextDisplaySettings.Types): PrefItemHandle =
            object : PrefItemHandle {
                override var value: Any = when (type) {
                    TextDisplaySettings.Types.FONTSIZE -> 16
                    TextDisplaySettings.Types.TOPMARGIN -> 5
                    TextDisplaySettings.Types.LINE_SPACING -> 16
                    TextDisplaySettings.Types.MARGINSIZE -> WorkspaceEntities.MarginSize(3, 3, 100)
                    TextDisplaySettings.Types.FONTFAMILY -> "serif"
                    TextDisplaySettings.Types.BOOKMARKS_HIDELABELS -> emptyList<IdType>()
                    TextDisplaySettings.Types.STRONGS, TextDisplaySettings.Types.PAGE_SCROLL_AMOUNT,
                    TextDisplaySettings.Types.SCROLL_HELPER_LINE_STYLE -> 0
                    else -> true
                }
                override val enabled = true
                override val visible = true
                override val iconKey: String? = null
                override fun setNonSpecific() = Unit
            }
        override fun string(key: TextDisplayString, vararg args: Any) = table.fmt(textDisplayStringKey(key), *args)
        override fun array(key: TextDisplayArray) = listOf("Default")
        override val fonts = listOf(SettingsItem.Choice("serif", "Serif"))
        override fun displaySettingChanged(type: TextDisplaySettings.Types) = Unit
        override fun contentSource(): ContentSource = error("No content access needed")
        override fun backgroundImageNames(): Map<String, String> = emptyMap()
    }

    @BeforeTest
    fun setUp() {
        stopKoin()
        startKoin { modules(module { single<TextDisplayPrefItems> { prefItems } }) }
    }

    @AfterTest
    fun tearDown() { stopKoin() }

    @Test
    fun fontSizeLabelUpdatesBeforeOk() = assertLiveLabel(TextSettingType.FONTSIZE, "16 pt", 24, "24 pt")

    @Test
    fun topMarginLabelUpdatesBeforeOk() = assertLiveLabel(TextSettingType.TOPMARGIN, "5 mm", 12, "12 mm")

    @Test
    fun lineSpacingLabelUsesTenthsBeforeOk() =
        assertLiveLabel(TextSettingType.LINE_SPACING, "Line spacing 1.6x", 23, "Line spacing 2.3x")

    private fun assertLiveLabel(type: TextSettingType, initial: String, next: Int, expected: String) = runComposeUiTest {
        val bundle = SettingsBundle(level = SettingsLevel.WORKSPACE)
        val rows = TextDisplaySettingsServiceImpl(DetachedWorkspaceEdit(bundle))
            .loadText(SettingsScope.Workspace(bundle.workspaceId.toString())).rows
        var committed: Int? = null
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    TextSettingRowEditorSheet(
                        pages = listOf(SettingsEditorPage.Row(type.name)),
                        rows = rows, dialogLabels = strings.buildTextDisplayScreenLabels(),
                        onNumericChange = { _, value -> committed = value },
                        onMarginsChange = { _, _, _, _ -> }, onRevert = {}, onPop = {}, onClose = {},
                    )
                }
            }
        }
        onNodeWithText(initial).assertIsDisplayed()
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(next.toFloat()) }
        onNodeWithText(expected).assertIsDisplayed()
        assertNull(committed, "Changing the slider must not commit before OK")
        onNodeWithText(strings.buildTextDisplayScreenLabels().okLabel).performClick()
        assertEquals(next, committed)
    }
}
