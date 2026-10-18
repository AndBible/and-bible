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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.host.slots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import net.bible.android.view.activity.navigation.GridPassageControllerFactory
import net.bible.sharedcore.docs.HelpVideos
import net.bible.sharedcore.reading.ReadingOverlay
import net.bible.sharedcore.reading.ReadingOverlayExclusion
import net.bible.sharedcore.speak.AdvancedSpeakSettingsController
import net.bible.sharedcore.speak.BibleSpeakSettingsController
import net.bible.sharedcore.speak.PickedVerse
import net.bible.sharedcore.speak.SpeakRangeEditor
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakSheetPage
import net.bible.sharedcore.speak.SpeakSheetStack
import net.bible.sharedcore.speak.sleepTimerSelectionFor
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.components.AbLinkRouting
import net.bible.sharedui.components.AbMessageDialog
import net.bible.sharedui.navigation.GridChoosePassageContent
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.speak.AdvancedSpeakSettingsContent
import net.bible.sharedui.speak.SleepTimerContent
import net.bible.sharedui.speak.SpeakRangeContent
import net.bible.sharedui.speak.SpeakSettingsContent
import net.bible.sharedui.speak.SpeakSettingsSheet
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import org.crosswire.jsword.passage.VerseFactory

/**
 * The reading view's Speak settings sheet: its page stack, the repeat-range draft, the controllers,
 * the help dialog state and the sheet body. Owned by [ReadingHostSlots]; everything here is
 * host-lived (plain fields, no `rememberSaveable`), exactly as it was in the Android host.
 *
 * [strings] is a lambda so a locale change is read when a help text is built. [platform] is the
 * system-TTS-settings port; [gridPassages] builds the verse picker's grid controller.
 */
@kotlin.native.HiddenFromObjC
internal class SpeakSettingsState(
    private val hostState: ReadingHostState,
    service: SpeakSettingsService,
    private val strings: () -> Strings,
    private val externalLinks: ExternalLinks,
    private val platform: SpeakSettingsPlatform,
    private val gridPassages: GridPassageControllerFactory,
) {
    /** The sheet's page stack. One instance per host. */
    val sheet = SpeakSheetStack()

    /**
     * The repeat-range draft. Deliberately host-lived rather than page-lived: its endpoints are
     * picked on the PickVerse page, so a draft that died with the RepeatRange page's composition
     * would lose the endpoint the user just chose.
     */
    private val rangeEditor = SpeakRangeEditor()
    private val settingsController = BibleSpeakSettingsController(service)
    private val advancedController = AdvancedSpeakSettingsController(service)

    /**
     * Non-null only while the verse-picker page is composed. [net.bible.sharedcore.navigation.GridChoosePassageController.back]
     * returns `false` at its own root, which is exactly "the grid has nothing left to pop" -- so the
     * sheet's dismiss can unwind BOOK, CHAPTER, VERSE first and only pop the sheet page once the
     * grid is exhausted. Without this, one dismiss would jump straight out of the picker.
     */
    internal var gridBack: (() -> Boolean)? = null

    /**
     * The Speak/Advanced-Speak help dialogs' shared state: ONE `MutableState` (only one of the two
     * sheet pages that opens either is ever composed at a time), rendered as a dialog OVER the sheet
     * (`ReadingOverlayExclusion`'s kdoc: dialog-over-sheet is fine).
     */
    val help = mutableStateOf<String?>(null)

    /** Opens the sheet on its Settings page, closing the other modal overlays first. */
    fun show() {
        // Total mutual exclusion, the third and last application site of the rule.
        ReadingOverlayExclusion.closedBy(ReadingOverlay.SpeakSheet).forEach(hostState::closeOverlay)
        // `open` assigns `listOf(page)`, so this always lands on the Settings page whatever depth
        // the sheet was left at -- reopening never resumes a half-finished range edit. The `close()`
        // is kept for symmetry with the text-settings editor (an explicit empty state before the
        // open keeps that guarantee obvious).
        sheet.close()
        sheet.open(SpeakSheetPage.Settings)
    }

    fun showHelp() {
        val s = strings()
        help.value = "<b>${s.speakTitle}</b><br><br>" +
            "<b><a href=\"${HelpVideos.SPEAK}\">${s.watchTutorialVideo}</a></b>"
    }

    /**
     * The ADVANCED page's help: the auto-bookmarking / playback-settings explanations, the two least
     * self-evident switches there.
     */
    fun showAdvancedHelp() {
        val s = strings()
        help.value = "<b>${s.confSpeakAutoBookmark}</b><br><br>" +
            "<b><a href=\"${HelpVideos.AUTOMATIC_SPEAK_BOOKMARKING}\">${s.watchTutorialVideo}</a></b><br><br>" +
            s.speakHelpAutoBookmark +
            "<br><br><b>${s.confSavePlaybackSettingsToBookmarks}</b><br><br>" +
            s.speakHelpPlaybackSettings +
            "<br><br>" + s.speakHelpPlaybackSettingsExample
    }

    /**
     * The sheet's dismiss (back press, scrim, drag): the verse-picker grid unwinds its own
     * BOOK, CHAPTER, VERSE steps first; only when it has none left does the sheet pop a page.
     */
    fun dismiss() {
        val back = gridBack
        if (back == null || !back()) sheet.pop()
    }

    @Composable
    fun Content() {
        val pages by sheet.pages.collectAsState()
        val page = pages.lastOrNull()
        val playback by settingsController.playback.collectAsState()
        SpeakSettingsSheet(
            page = page,
            depth = pages.size,
            onDismiss = ::dismiss,
            onClose = { sheet.close() },
        ) { current, scroll ->
            when (current) {
                SpeakSheetPage.Settings -> SpeakSettingsContent(
                    playback = playback,
                    onSpeedChange = settingsController::setSpeed,
                    onSpeakChapterChanges = settingsController::setSpeakChapterChanges,
                    onSpeakTitles = settingsController::setSpeakTitles,
                    onSpeakFootnotes = settingsController::setSpeakFootnotes,
                    onOpenRepeatRange = {
                        rangeEditor.seed(playback.repeatRangeStart, playback.repeatRangeEnd)
                        sheet.push(SpeakSheetPage.RepeatRange)
                    },
                    onOpenSleepTimer = { sheet.push(SpeakSheetPage.SleepTimer) },
                    onOpenAdvanced = { sheet.push(SpeakSheetPage.Advanced) },
                    onSystemTtsSettings = { platform.openSystemTtsSettings() },
                    onHelp = ::showHelp,
                    // Round 14b 7.b: the sheet shell owns this state so it can fade the clip.
                    scrollState = scroll,
                )
                SpeakSheetPage.Advanced -> {
                    val advanced by advancedController.advanced.collectAsState()
                    AdvancedSpeakSettingsContent(
                        advanced = advanced,
                        onSynchronize = advancedController::setSynchronize,
                        onReplaceDivineName = advancedController::setReplaceDivineName,
                        onAutoBookmark = advancedController::setAutoBookmark,
                        onRestoreSettingsFromBookmarks = advancedController::setRestoreSettingsFromBookmarks,
                        onHelp = ::showAdvancedHelp,
                        scrollState = scroll,
                    )
                }
                SpeakSheetPage.RepeatRange -> {
                    val start by rangeEditor.start.collectAsState()
                    val end by rangeEditor.end.collectAsState()
                    val orderError by rangeEditor.showOrderError.collectAsState()
                    val canCommit by rangeEditor.canCommit.collectAsState()
                    SpeakRangeContent(
                        start = start,
                        end = end,
                        showOrderError = orderError,
                        canCommit = canCommit,
                        onPickStart = { sheet.push(SpeakSheetPage.PickVerse(end = false)) },
                        onPickEnd = { sheet.push(SpeakSheetPage.PickVerse(end = true)) },
                        onClear = {
                            rangeEditor.clearDraft()
                            settingsController.clearRepeatRange()
                            sheet.pop()
                        },
                        onConfirm = {
                            // Read the flows, not the collected values: `onConfirm` can fire in the
                            // same frame a pick landed, before this composition has recomposed.
                            // The binding rule is `SpeakRangeEditor.canCommit` -- both endpoints set
                            // AND end.ordinal > start.ordinal -- and it is read from the same
                            // snapshot as the endpoints. This must not restate a laxer version
                            // of it: the ordering rule lives in :sharedCore, once.
                            val s = rangeEditor.start.value
                            val e = rangeEditor.end.value
                            if (rangeEditor.canCommit.value && s != null && e != null) {
                                settingsController.setRepeatRange(s.osisId, e.osisId)
                            }
                            sheet.pop()
                        },
                        onCancel = { sheet.pop() },
                    )
                }
                is SpeakSheetPage.PickVerse -> VersePickerPage(current.end)
                SpeakSheetPage.SleepTimer -> SleepTimerContent(
                    selection = sleepTimerSelectionFor(playback.sleepTimerMinutes),
                    // A never-set timer has no stored value to open the custom slider on, so fall
                    // back to the last one the user chose (`lastSleepTimer`, classic's own memory).
                    customMinutes = playback.sleepTimerMinutes.takeIf { it > 0 }
                        ?: playback.lastSleepTimerMinutes,
                    onPick = { minutes, closeAfter ->
                        settingsController.setSleepTimerMinutes(minutes)
                        // `closeAfter` comes from the CHIP, not from the value: a custom slider
                        // released on exactly 30 is a preset NUMBER but not a chip tap, and popping
                        // on the number closed the page in the middle of an adjustment. Off and the
                        // presets commit outright; a custom drag stays on the page.
                        if (closeAfter) sheet.pop()
                    },
                )
            }
        }
        // A dialog over the sheet (ReadingOverlayExclusion's kdoc) -- see [help].
        help.value?.let { html ->
            AbLinkRouting(
                askFirst = externalLinks.askBeforeOpening,
                onOpenExternal = { externalLinks.open(it) },
            ) {
                AbMessageDialog(
                    title = null,
                    html = html,
                    confirmText = strings().okay,
                    onConfirm = { help.value = null },
                    onDismissRequest = { help.value = null },
                )
            }
        }
    }

    /**
     * The passage grid as a sheet page, picking ONE endpoint of the repeat range. A fresh controller
     * per endpoint (spec 6.5 point 4): entering the page always starts at BOOK, so the step
     * back-stack needs no reset logic of its own.
     */
    @Composable
    private fun VersePickerPage(pickingEnd: Boolean) {
        val localStrings = LocalStrings.current
        val title = if (pickingEnd) localStrings.speakEndingOfPassage else localStrings.speakBeginningOfPassage
        val gridScope = rememberCoroutineScope()
        val controller = remember(pickingEnd) {
            gridPassages.create(
                scope = gridScope,
                baseTitle = title,
                isScripture = true,
                navigateToVerse = true,
                onFinish = { osisId ->
                    val verse = VerseFactory.fromString(gridPassages.versification, osisId)!!
                    rangeEditor.set(pickingEnd, PickedVerse(verse.getOsisID(), verse.getName(), verse.ordinal))
                    sheet.pop()
                },
            )
        }
        DisposableEffect(controller) {
            gridBack = controller::back
            onDispose { gridBack = null }
        }
        val ui by controller.ui.collectAsState()
        // The sheet shell bounds the body to 400dp, so `fillMaxSize()` here is bounded and safe --
        // the grid sizes its cells against that height.
        val rendered = ui
        GridChoosePassageContent(rendered, { id -> rendered?.let { controller.pick(id, it) } }, Modifier.fillMaxSize())
    }
}
