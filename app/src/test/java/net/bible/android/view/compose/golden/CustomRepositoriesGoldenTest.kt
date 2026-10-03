package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.download.CustomRepoListState
import net.bible.sharedcore.download.CustomRepositoryData
import net.bible.sharedcore.download.EditorState
import net.bible.sharedcore.download.RepoRow
import net.bible.sharedcore.download.Validation
import net.bible.sharedui.download.CustomRepositoriesScreen
import net.bible.sharedui.download.CustomRepositoryEditorScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class CustomRepositoriesGoldenTest {

    private val rows = listOf(
        RepoRow(id = 1, name = "My Custom Repo", description = "https://example.com/repo/manifest.json"),
        RepoRow(id = 2, name = "Another Repository", description = "https://repo.example.org/sword"),
        RepoRow(id = 3, name = "MyBible Feed", description = "https://mybible.example.net/feed.json"),
    )

    @Composable
    private fun listScreen(rows: List<RepoRow> = this.rows) = CustomRepositoriesScreen(
        state = CustomRepoListState(rows),
        onRowClick = {},
        onCreate = {},
        onUp = {},
    )

    @Test fun list_populated() = captureMatrix("CustomRepositories", "populated") { listScreen() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun list_populated_rtl() = captureRtl("CustomRepositories", "populated") { listScreen() }

    @Test fun list_empty() =
        captureGolden("CustomRepositories", "empty", EDGE_MODE) { listScreen(rows = emptyList()) }

    private val resolvedRepo = CustomRepositoryData(
        id = 0,
        name = "example.com-a1b",
        description = "https://example.com/repo/manifest.json",
        type = "sword-https",
        host = "example.com",
        catalogDirectory = "/repo",
        packageDirectory = "/repo/packages",
        manifestUrl = "https://example.com/repo/manifest.json",
    )

    @Composable
    private fun editorScreen(state: EditorState) = CustomRepositoryEditorScreen(
        state = state,
        onUrlChange = {},
        onPaste = {},
        onPackageDirChange = {},
        onSave = {},
        onDelete = {},
        onUp = {},
    )

    @Test fun editor_idle() =
        captureGolden("CustomRepositoryEditor", "idle", EDGE_MODE) { editorScreen(EditorState()) }

    // NOTE: no `editor_validating` golden — the Validating state's trailing CircularProgressIndicator
    // is an indeterminate spinner whose animation phase is non-deterministic across record/verify runs
    // (inspectionMode does not fully freeze it), so it flakes the golden. The transient validating state
    // has no meaningful parity value beyond "a spinner appears"; idle/valid/invalid cover the editor.
    @Test fun editor_invalid() =
        captureGolden("CustomRepositoryEditor", "invalid", EDGE_MODE) {
            editorScreen(
                EditorState(url = "https://not-a-real-repo.example", validation = Validation.Invalid),
            )
        }

    private val validState = EditorState(
        url = "https://example.com/repo/manifest.json",
        validation = Validation.Valid,
        resolved = resolvedRepo,
        packageDirectory = resolvedRepo.packageDirectory,
        isDirty = true,
        canSave = true,
        isExisting = false,
    )

    /** "valid" is the editor's primary/most illustrative state (mirrors `LabelEditGoldenTest`'s
     *  "primary" / `DocumentSelectionGoldenTest`'s "populated" convention): full 4-mode matrix + RTL,
     *  while the other states (idle/invalid) are edge states captured single-mode only. */
    @Test fun editor_valid() = captureMatrix("CustomRepositoryEditor", "valid") { editorScreen(validState) }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun editor_valid_rtl() = captureRtl("CustomRepositoryEditor", "valid") { editorScreen(validState) }
}
