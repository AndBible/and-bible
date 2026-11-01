package net.bible.android.view.compose.golden

import net.bible.sharedui.bookmark.LabelIdentitySheetContent
import net.bible.sharedui.components.AbCreateItemSheetContent
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.components.AbActionSheetContent
import net.bible.sharedui.components.AbActionSheetRow
import net.bible.sharedui.components.AbChoiceSheetContent
import net.bible.sharedui.components.AbSearchableOptionSheetContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Content only: an open modal must never be passed to a golden capture. Width is 320 dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AppWideSheetChromeGoldenTest {
    @Test fun createRtl() = captureRtl("AppWideSheetChrome", "create", heightDp = 360) {
        SheetSurface {
            AbCreateItemSheetContent("Create new document with a long title", "Name", "Create", "Import", {}, {})
        }
    }

    @Test fun labelIdentity() = captureMatrix("AppWideSheetChrome", "labelIdentity", heightDp = 500) {
        SheetSurface {
            LabelIdentitySheetContent("Study", true, 0xff808080.toInt(), null, emptyList(), false,
                {}, {}, {}, iconSlot = { _, _ -> })
        }
    }

    @Test fun labelIdentityRtl() = captureRtl("AppWideSheetChrome", "labelIdentity", heightDp = 500) {
        SheetSurface {
            LabelIdentitySheetContent("Study", true, 0xff808080.toInt(), null, emptyList(), false,
                {}, {}, {}, iconSlot = { _, _ -> })
        }
    }

    @Test fun untitledChoice() = captureMatrix("AppWideSheetChrome", "untitledChoice", heightDp = 220) {
        SheetSurface {
            AbChoiceSheetContent("", listOf(SettingsItem.Choice("a", "First option with a long label"),
                SettingsItem.Choice("b", "Second option")), "a", {}, {})
        }
    }

    @Test fun untitledAction() = captureMatrix("AppWideSheetChrome", "untitledAction", heightDp = 200) {
        SheetSurface {
            AbActionSheetContent("", onClose = {}) {
                AbActionSheetRow("First action with a long label", onClick = {})
                AbActionSheetRow("Second action", onClick = {})
            }
        }
    }

    @Test fun untitledActionRtl() = captureRtl("AppWideSheetChrome", "untitledAction", heightDp = 200) {
        SheetSurface {
            AbActionSheetContent("", onClose = {}) {
                AbActionSheetRow("First action with a long label", onClick = {})
                AbActionSheetRow("Second action", onClick = {})
            }
        }
    }

    @Test fun searchablePicker() = captureMatrix("AppWideSheetChrome", "searchablePicker", heightDp = 240) {
        SheetSurface {
            AbSearchableOptionSheetContent(listOf("English", "Finnish"), "English", { it }, {},
                searchPlaceholder = "Search")
        }
    }

    @Test fun searchablePickerRtl() = captureRtl("AppWideSheetChrome", "searchablePicker", heightDp = 240) {
        SheetSurface {
            AbSearchableOptionSheetContent(listOf("English", "Finnish"), "English", { it }, {},
                searchPlaceholder = "Search")
        }
    }
}
