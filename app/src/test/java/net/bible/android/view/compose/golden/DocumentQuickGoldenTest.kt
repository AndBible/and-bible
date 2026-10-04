package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedui.components.AbQuickSheetContent
import net.bible.sharedui.components.AbQuickSheetFooterRow
import net.bible.sharedui.components.AbQuickSheetTab
import net.bible.sharedui.components.AbSheetContentMaxHeight
import net.bible.sharedui.navigation.DocumentQuickContent
import net.bible.sharedui.strings.LocalStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers round 15b Plan B Task 4's [DocumentQuickContent] — the document quick sheet's row list.
 *
 * [LocalCategoryIcon][net.bible.sharedui.navigation.LocalCategoryIcon] and
 * [LocalStrings] are already supplied by every capture via `GoldenHarness`'s `ProvideAppLocals`
 * (A5): no extra provider wrapper is needed here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class DocumentQuickGoldenTest {

    private val english = LangOption("en", "English", "en")
    private val greek = LangOption("grc", "Greek", "grc")
    private val latin = LangOption("la", "Latin", "la")

    /**
     * A3: at least 10 rows, mixing a Bible, a commentary and a dictionary, so the list genuinely
     * OVERFLOWS [AbSheetContentMaxHeight] (400dp) rather than merely fitting inside it — a short
     * list would prove nothing about clipping or the bottom fade.
     */
    private fun sampleRows(): List<DocRow> = listOf(
        DocRow(
            docId = "KJV", osisId = "KJV", abbreviation = "KJV", name = "King James Version",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 4.2,
        ),
        DocRow(
            docId = "ESV2011", osisId = "ESV2011", abbreviation = "ESV", name = "English Standard Version",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 3.8,
        ),
        DocRow(
            docId = "MHC", osisId = "MHC", abbreviation = "MHC", name = "Matthew Henry Commentary",
            language = english, repository = "CrossWire", category = DocCategory.COMMENTARY,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 12.5,
        ),
        DocRow(
            docId = "StrongsGreek", osisId = "StrongsGreek", abbreviation = "Strong", name = "Strong's Greek Dictionary",
            language = greek, repository = "CrossWire", category = DocCategory.DICTIONARY,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 1.1,
        ),
        DocRow(
            docId = "NIV", osisId = "NIV", abbreviation = "NIV", name = "New International Version",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 4.0,
        ),
        DocRow(
            docId = "NET", osisId = "NET", abbreviation = "NET", name = "New English Translation",
            language = english, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 5.6,
        ),
        DocRow(
            docId = "MPC", osisId = "MPC", abbreviation = "MPC", name = "Matthew Poole's Commentary",
            language = english, repository = "CrossWire", category = DocCategory.COMMENTARY,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 9.3,
        ),
        DocRow(
            docId = "StrongsHebrew", osisId = "StrongsHebrew", abbreviation = "Strong-H", name = "Strong's Hebrew Dictionary",
            language = latin, repository = "CrossWire", category = DocCategory.DICTIONARY,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 1.3,
        ),
        DocRow(
            docId = "Vulgate", osisId = "Vulgate", abbreviation = "Vulg", name = "Latin Vulgate",
            language = latin, repository = "CrossWire", category = DocCategory.BIBLE,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 3.1,
        ),
        DocRow(
            docId = "TSK", osisId = "TSK", abbreviation = "TSK", name = "Treasury of Scripture Knowledge",
            language = english, repository = "CrossWire", category = DocCategory.COMMENTARY,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 6.4,
        ),
        DocRow(
            docId = "AbbottSmith", osisId = "AbbottSmith", abbreviation = "AbSm", name = "Abbott-Smith Greek Lexicon",
            language = greek, repository = "CrossWire", category = DocCategory.DICTIONARY,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 2.2,
        ),
        DocRow(
            docId = "RWP", osisId = "RWP", abbreviation = "RWP", name = "Robertson's Word Pictures",
            language = english, repository = "CrossWire", category = DocCategory.COMMENTARY,
            installStatus = DocInstallStatus.INSTALLED, percentDone = 0, recommended = false,
            badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = 7.7,
        ),
    )

    @Test fun documentQuick_recent() = captureMatrix("DocumentQuick", "recent") {
        Box(Modifier.heightIn(max = AbSheetContentMaxHeight)) {
            DocumentQuickContent(rows = sampleRows(), currentDocId = "KJV", onSelect = {})
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun documentQuick_recent_rtl() = captureRtl("DocumentQuick", "recent") {
        Box(Modifier.heightIn(max = AbSheetContentMaxHeight)) {
            DocumentQuickContent(rows = sampleRows(), currentDocId = "KJV", onSelect = {})
        }
    }

    /**
     * A4: the content composed INSIDE the real shell — the only capture where the bottom fade
     * (owned by `AbQuickSheetContent`'s scroll bound, not by [DocumentQuickContent] itself) and the
     * three tab labels at the harness's 320dp width are actually checkable.
     *
     * `heightDp = 650` (the default viewport is 470dp): header (~48dp) + tabs (~48dp) + the 400dp
     * scroll bound + divider + footer add up to ~565dp, so at the default height the fade's own
     * 24dp window — the LAST 24dp of that 400dp bound — falls entirely BELOW the capture's bottom
     * edge and the footer is never drawn into frame at all. A bare `captureGolden` call here would
     * pass while proving nothing about either finding, the exact trap A3/A4 warn about. Confirmed
     * against the recorded PNG (see the task report) that this height actually shows both.
     */
    @Test fun documentQuick_inSheet() = captureGolden("DocumentQuick", "inSheet", GoldenMode.LIGHT, heightDp = 650) {
        SheetSurface {
            AbQuickSheetContent(
                title = "Documents",
                onClose = {},
                tabs = listOf(
                    AbQuickSheetTab("recent", LocalStrings.current.documentTabRecent),
                    AbQuickSheetTab("forVerse", LocalStrings.current.documentTabForVerse),
                    AbQuickSheetTab("lastFilter", LocalStrings.current.documentTabLastFilter),
                ),
                selectedTabId = "recent",
                canScrollForward = { true },
                footer = { AbQuickSheetFooterRow(text = LocalStrings.current.allDocuments, onClick = {}) },
            ) {
                DocumentQuickContent(rows = sampleRows(), currentDocId = "KJV", onSelect = {})
            }
        }
    }

    /** The Bible toolbar button's scoped sheet: Recent / This verse / All, Bibles only. */
    @Test fun documentQuick_inSheet_bibleScope() = captureMatrix("DocumentQuick", "inSheet_bibleScope", heightDp = 650) {
        SheetSurface {
            AbQuickSheetContent(
                title = "Bible",
                onClose = {},
                tabs = listOf(
                    AbQuickSheetTab("RECENT", LocalStrings.current.documentTabRecent),
                    AbQuickSheetTab("FOR_VERSE", LocalStrings.current.documentTabForVerse),
                    AbQuickSheetTab("ALL", LocalStrings.current.documentTabAll),
                ),
                selectedTabId = "ALL",
                canScrollForward = { false },
                footer = { AbQuickSheetFooterRow(text = LocalStrings.current.allDocuments, onClick = {}) },
            ) {
                DocumentQuickContent(
                    rows = sampleRows().filter { it.category == DocCategory.BIBLE },
                    currentDocId = "KJV",
                    onSelect = {},
                )
            }
        }
    }
}
