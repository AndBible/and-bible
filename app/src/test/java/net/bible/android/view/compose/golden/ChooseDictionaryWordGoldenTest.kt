package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.DictRow
import net.bible.sharedui.navigation.ChooseDictionaryWordScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ChooseDictionaryWordGoldenTest {
    private val all = listOf(
        DictRow("0", "G4102 πίστις"),
        DictRow("1", "G1680 ἐλπίς"),
        DictRow("2", "G26 ἀγάπη"),
        DictRow("3", "H0530 emunah"),
    )
    // Fixed synchronous snippet for deterministic goldens.
    private val snippet: suspend (String) -> String = { "faith, assurance, belief, fidelity" }

    @Test fun dictionary_populated() {
        captureMatrix("ChooseDictionaryWord", "populated") {
            ChooseDictionaryWordScreen(
                "Dictionary", "Search", loading = false, query = "", rows = all, error = null,
                loadSnippet = snippet, onQueryChange = {}, onSelect = {}, onDismissError = {}, onNavigateUp = {},
            )
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun dictionary_populated_rtl() {
        captureRtl("ChooseDictionaryWord", "populated") {
            ChooseDictionaryWordScreen(
                "Dictionary", "Search", loading = false, query = "", rows = all, error = null,
                loadSnippet = snippet, onQueryChange = {}, onSelect = {}, onDismissError = {}, onNavigateUp = {},
            )
        }
    }

    @Test fun dictionary_loading() {
        captureGolden("ChooseDictionaryWord", "loading", EDGE_MODE) {
            ChooseDictionaryWordScreen(
                "Dictionary", "Search", loading = true, query = "", rows = emptyList(), error = null,
                loadSnippet = snippet, onQueryChange = {}, onSelect = {}, onDismissError = {}, onNavigateUp = {},
            )
        }
    }

    @Test fun dictionary_filtered() {
        captureGolden("ChooseDictionaryWord", "filtered", EDGE_MODE) {
            ChooseDictionaryWordScreen(
                "Dictionary", "Search", loading = false, query = "πίστ", rows = listOf(all[0]), error = null,
                loadSnippet = snippet, onQueryChange = {}, onSelect = {}, onDismissError = {}, onNavigateUp = {},
            )
        }
    }
}
