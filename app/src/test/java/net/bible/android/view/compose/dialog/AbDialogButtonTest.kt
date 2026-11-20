package net.bible.android.view.compose.dialog

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.dialog.AbDialogButton
import net.bible.sharedui.components.dialog.DialogButtonLook
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.DialogButtonRoleKey
import net.bible.sharedui.components.dialog.dialogButtonLook
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbDialogButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun destructiveUsesErrorColour() {
        val s = lightColorScheme()
        assertEquals(DialogButtonLook(s.error, FontWeight.Medium), dialogButtonLook(DialogButtonRole.Destructive, mono = false, scheme = s))
    }

    @Test fun destructiveInMonochromeIsBoldInk() {
        val s = lightColorScheme()
        assertEquals(DialogButtonLook(s.onSurface, FontWeight.Bold), dialogButtonLook(DialogButtonRole.Destructive, mono = true, scheme = s))
    }

    @Test fun nonDestructiveRolesUsePrimary() {
        val s = lightColorScheme()
        listOf(DialogButtonRole.Cancel, DialogButtonRole.Neutral, DialogButtonRole.Confirm).forEach {
            assertEquals(s.primary, dialogButtonLook(it, mono = false, scheme = s).color)
        }
    }

    @Test fun buttonCarriesRoleTagAndSemantics() {
        compose.setContent { AbTheme { AbDialogButton("Delete", DialogButtonRole.Destructive, {}) } }
        compose.onNodeWithTag("dialog-confirm")
            .assert(SemanticsMatcher.expectValue(DialogButtonRoleKey, DialogButtonRole.Destructive))
    }
}
