package net.bible.android.view.compose.dialog

import androidx.compose.runtime.Composable
import net.bible.sharedcore.ui.dialog.DialogAnswer
import net.bible.sharedui.components.AbOptionsDialog

/** Local equivalent of the desktop updater: six actions and a persistent checkbox choice. */
val updateDialogAnswers = listOf(
    DialogAnswer("download", "Download"),
    DialogAnswer("tomorrow", "Remind me tomorrow"),
    DialogAnswer("week", "Remind me in a week"),
    DialogAnswer("month", "Remind me in a month"),
    DialogAnswer("skip", "Skip this version"),
    DialogAnswer("news", "What's new"),
)

@Composable
fun ManyActionOptionsFixture(onSelect: (String, Boolean) -> Unit = { _, _ -> }) = AbOptionsDialog(
    title = "New version available",
    message = "AndBible production-99999 is available. You have 6.0.1266.",
    options = updateDialogAnswers,
    onSelect = {},
    onDismissRequest = {},
    cancellable = false,
    checkboxLabel = "Don't notify me about updates",
    onSelectWithCheckbox = onSelect,
)
