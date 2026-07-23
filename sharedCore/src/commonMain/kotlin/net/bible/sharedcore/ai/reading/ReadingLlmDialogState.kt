package net.bible.sharedcore.ai.reading

/**
 * What the host renders for the reading-view AI dialogs. `run`/`regen` carry the host callbacks
 * captured at open time (added by the controller, Task 3), so the composable (Task 4) only ever
 * needs the [ReadingLlmDialogState] to decide what to show.
 */
sealed interface ReadingLlmDialog {
    /** No dialog showing. */
    data object None : ReadingLlmDialog

    /** The grouped prompt-selector list (long-press / menu entry point). */
    data class PromptSelector(val groups: List<ReadingPromptGroupVd>) : ReadingLlmDialog

    /** Free-text prompt for a prompt whose `specifyBeforeRun` is set. */
    data class SpecifyBeforeRun(val promptId: String, val promptName: String) : ReadingLlmDialog

    /** Model-selection dialog, shown either before running a prompt or before regenerating a page. */
    data class ModelSelection(val models: List<ReadingModelVd>, val allowSetDefault: Boolean) : ReadingLlmDialog

    /** Confirm-regenerate dialog for an existing AI-generated page. */
    data class Regenerate(val pageId: String) : ReadingLlmDialog
}

/** Single source of truth for which reading-view AI dialog (if any) is currently shown. */
data class ReadingLlmDialogState(val dialog: ReadingLlmDialog = ReadingLlmDialog.None)
