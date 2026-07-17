/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.speak

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.FrameLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.speak.load
import net.bible.android.control.speak.save
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.common.htmlToSpan
import net.bible.service.common.speakHelpVideo
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.speak.BibleSpeakSettingsController
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.speak.BibleSpeakScreen
import net.bible.sharedui.theme.AbTheme
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.passage.VerseRange
import org.koin.android.ext.android.inject

/**
 * Compose host for the main Speak screen (classic BibleSpeakActivity). Owns the Android-typed bits
 * the shared screen delegates: the sleep-timer number picker, the two-step repeat-passage verse
 * picker (via GridChoosePassageBook, identical to classic), the system-TTS intent, and the help
 * dialog. The in-Activity transport widget is intentionally not ported (see plan Global Constraints).
 */
class BibleSpeakComposeActivity : ActivityBase() {
    private val service: SpeakSettingsService by inject()
    private val navigationControl: NavigationControl by inject()

    private val controller by lazy {
        BibleSpeakSettingsController(
            service = service,
            onSleepTimerToggle = ::onSleepTimerToggle,
            onChooseRepeatRange = ::startRepeatRangeFlow,
        )
    }

    private var startVerse: Verse? = null
    private var endVerse: Verse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val playback by controller.playback.collectAsState()
                    BibleSpeakScreen(
                        playback = playback,
                        onSpeedChange = controller::setSpeed,
                        onSpeakChapterChanges = controller::setSpeakChapterChanges,
                        onSpeakTitles = controller::setSpeakTitles,
                        onSpeakFootnotes = controller::setSpeakFootnotes,
                        onSleepTimerToggle = controller::setSleepTimerEnabled,
                        onToggleRepeatRange = controller::toggleRepeatRange,
                        onOpenAdvanced = { startActivity(Intent(this, SpeakSettingsComposeActivity::class.java)) },
                        onSystemTtsSettings = { startActivity(Intent("com.android.settings.TTS_SETTINGS")) },
                        onHelp = ::showHelp,
                        onNavigateUp = { onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }
    }

    /** true = show the minute picker (sets sleepTimer); false = turn it off (sleepTimer = 0). */
    private fun onSleepTimerToggle(enabled: Boolean) {
        if (!enabled) {
            SpeakSettings.load().apply { sleepTimer = 0; save(updateBookmark = true) }
            return
        }
        val settings = SpeakSettings.load()
        val picker = NumberPicker(this).apply { minValue = 1; maxValue = 120; value = settings.lastSleepTimer }
        val layout = FrameLayout(this).apply { addView(picker) }
        AlertDialog.Builder(this)
            .setView(layout)
            .setTitle(R.string.sleep_timer_title)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                SpeakSettings.load().apply {
                    sleepTimer = picker.value; lastSleepTimer = picker.value; save(updateBookmark = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)  // state-driven: no change if cancelled
            .show()
    }

    /** Classic setRepeatPassage: choose start verse (then end verse via onActivityResult). */
    private fun startRepeatRangeFlow() {
        startVerse = null; endVerse = null
        val intent = ScreenLauncher.intentFor(this, Screen.GridChoosePassageBook).apply {
            putExtra("isScripture", true)
            putExtra("navigateToVerse", true)
            putExtra("title", getString(R.string.speak_beginning_of_passage))
        }
        startActivityForResult(intent, STD_REQUEST_CODE)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val verseStr = data?.extras?.getString("verse")
        val v11n = navigationControl.versification
        if (verseStr != null) {
            val verse = VerseFactory.fromString(v11n, verseStr)
            if (startVerse == null) {
                startVerse = verse
                val intent = ScreenLauncher.intentFor(this, Screen.GridChoosePassageBook).apply {
                    putExtra("isScripture", true)
                    putExtra("navigateToVerse", true)
                    putExtra("title", getString(R.string.speak_ending_of_passage))
                }
                startActivityForResult(intent, STD_REQUEST_CODE)
            } else {
                endVerse = verse
                val settings = SpeakSettings.load()
                if (endVerse!!.ordinal > startVerse!!.ordinal) {
                    settings.playbackSettings.verseRange = VerseRange(v11n, startVerse, endVerse)
                    settings.save(updateBookmark = true)
                } else {
                    startVerse = null; endVerse = null
                    ABEventBus.post(ToastEvent(R.string.speak_ending_verse_must_be_later))
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showHelp() {
        val html = ("<b>${getString(R.string.speak)}</b><br><br>"
            + "<b><a href=\"$speakHelpVideo\">${getString(R.string.watch_tutorial_video)}</a></b>")
        val d = AlertDialog.Builder(this).setMessage(htmlToSpan(html))
            .setPositiveButton(android.R.string.ok) { _, _ -> }.create()
        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }
}
