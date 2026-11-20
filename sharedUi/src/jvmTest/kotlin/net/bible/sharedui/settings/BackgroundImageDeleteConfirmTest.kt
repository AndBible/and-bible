package net.bible.sharedui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.DialogButtonRoleKey
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/** The background-image delete confirmation must have a destructive confirm button. */
@OptIn(ExperimentalTestApi::class)
class BackgroundImageDeleteConfirmTest {
    private val labels = BackgroundImageChooserLabels.forTest()
    private val option = BackgroundImageOption("img1", "Image one", "tok")

    @Test fun deleteConfirmHasNoDetailAndHasDestructiveConfirm() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false) {
                    BackgroundImageChooserScreen(
                        options = listOf(option), labels = labels, loading = false, deleteConfirm = option,
                        thumbnailFor = { null }, onUp = {}, onSelect = {}, onImport = {},
                        onRequestDelete = {}, onConfirmDelete = {}, onDismissDelete = {},
                    )
                }
            }
        }
        onNodeWithText(labels.deleteConfirm).assertExists()
        onNodeWithTag("dialog-confirm").assert(
            SemanticsMatcher("role is Destructive") { it.config.getOrNull(DialogButtonRoleKey) == DialogButtonRole.Destructive },
        )
    }
}
