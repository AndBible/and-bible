/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedcore.reading

/**
 * A MODAL overlay of the reading view — one that renders in its own window over a scrim, so that
 * two of them open at once is the sheet-over-sheet violation this port bans (round 14a, spec §5).
 *
 * What is deliberately NOT in here, and why each omission is a decision rather than an oversight:
 *
 * - **The F6 search surface.** It is a `BottomSheetScaffold` (`ComposeReadingViewHost.kt:2373`) —
 *   in-flow, not a `Popup`, not modal — so it does not compete for modality and its behaviour is
 *   unchanged by this rule.
 * - **The agent log panel.** A hand-rolled `Surface` with a `draggable`, deliberately not a sheet
 *   (`2026-08-14-…-drawer-and-agent-panel-design.md:197-216` answered that with four code-verified
 *   reasons), and the stacked-bottom-surface fix of spec §6 depends on it staying visible UNDERNEATH
 *   an open sheet.
 * - **Dialogs.** "Dialog over sheet" is fine and unaffected: an `AlertDialog` opens in its own window
 *   and stacks over a `ModalBottomSheet` without issue (`ColorSettingsEditorSheet.kt:56-68`), so the
 *   specify-before-run, regenerate, confirm, info and error dialogs are all outside this rule.
 */
enum class ReadingOverlay {
    /** The reading-view AI sheets: the prompt selector and the model chooser. */
    Llm,

    /** Round 13a's Speak settings sheet (`SpeakSettingsSheet`, driven by `SpeakSheetStack`). */
    SpeakSheet,

    /** Round 12c's in-place text-settings editor sheet (driven by `SettingsEditorStack`). */
    TextSettingsEditor,

    /**
     * Round 15b's quick sheets — History, workspace switch, document switch, key chooser — which
     * share ONE state ([ReadingQuickSheet]) and therefore one member here. See that type's kdoc.
     */
    QuickSheet,
}

/**
 * Which modal overlays must close when one opens.
 *
 * The rule is total mutual exclusion and it is stated as a function rather than left implicit at
 * each call site for two reasons: the three overlays are gated by three INDEPENDENT states, so
 * nothing structural stops two being open; and a fourth modal overlay added later gets caught here
 * (and by [ReadingOverlayExclusionTest]) instead of quietly becoming the first pair nobody
 * arbitrates.
 *
 * Pure and host-agnostic — it names overlays, it does not close them. The host applies the decision;
 * see `ComposeReadingViewHost`'s `ReadingLlmDialogController(onSheetOpening = …)` wiring.
 */
object ReadingOverlayExclusion {
    fun closedBy(opening: ReadingOverlay): Set<ReadingOverlay> =
        ReadingOverlay.entries.toSet() - opening
}
