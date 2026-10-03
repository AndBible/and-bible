package net.bible.android.view.compose.golden

import net.bible.sharedcore.docs.DocsLinks
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.AiDocGroupVd
import net.bible.sharedcore.ai.AiDocVd
import net.bible.sharedui.ai.AiDocumentFilterScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AiDocumentFilterGoldenTest {

    // Bible/Commentary/Dictionary order per AiDocGroupVd's kdoc; some docs excluded (allowed=false)
    // so both checked and unchecked rows render.
    private val groups = listOf(
        AiDocGroupVd(
            categoryId = "BIBLE", categoryLabel = "Bibles",
            docs = listOf(
                AiDocVd(initials = "KJV", name = "King James Version", allowed = true),
                AiDocVd(initials = "ESV", name = "English Standard Version", allowed = true),
                AiDocVd(initials = "NET", name = "New English Translation", allowed = false),
            ),
        ),
        AiDocGroupVd(
            categoryId = "COMMENTARY", categoryLabel = "Commentaries",
            docs = listOf(
                AiDocVd(initials = "MHC", name = "Matthew Henry Commentary", allowed = false),
            ),
        ),
        AiDocGroupVd(
            categoryId = "DICTIONARY", categoryLabel = "Dictionaries",
            docs = listOf(
                AiDocVd(initials = "STR", name = "Strong's Greek Dictionary", allowed = true),
            ),
        ),
    )

    private fun screen(
        groups: List<AiDocGroupVd> = this.groups,
        isDirty: Boolean = true,
        initiallyHelpDialogOpen: Boolean = false,
    ) =
        @androidx.compose.runtime.Composable {
            AiDocumentFilterScreen(
                groups = groups,
                isDirty = isDirty,
                onUp = {},
                onToggle = {},
                onResetAll = {},
                onSave = {},
                helpBody = "Choose which installed documents the AI can read from when answering questions.",
                helpReadMoreUrl = DocsLinks.page("ai", "available-data-and-documents"),
                initiallyHelpDialogOpen = initiallyHelpDialogOpen,
            )
        }

    // heightDp=900: 3 category groups x 1-3 docs each, plus headers/dividers -- the default
    // viewport clips the Dictionaries group otherwise.
    @Test fun populated_matrix() =
        captureMatrix("AiDocumentFilter", "populated", heightDp = 900, content = screen())

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun populated_rtl() =
        captureRtl("AiDocumentFilter", "populated", heightDp = 900, content = screen())

    @Test fun no_documents_installed() =
        captureGolden("AiDocumentFilter", "empty", EDGE_MODE, content = screen(groups = emptyList(), isDirty = false))

    // F30: the overflow "Help" item opens an AbInfoDialog (with a "Read more" docs link), replacing
    // classic's CommonUtils.showHelpDialog AlertDialog.
    @Test fun help_matrix() =
        captureMatrix("AiDocumentFilter", "help", heightDp = 900, content = screen(initiallyHelpDialogOpen = true))
}
