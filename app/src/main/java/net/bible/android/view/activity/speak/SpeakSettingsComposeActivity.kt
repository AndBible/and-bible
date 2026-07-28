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

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.common.automaticSpeakBookmarkingVideo
import net.bible.service.common.htmlToSpan
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.speak.AdvancedSpeakSettingsController
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakTransportController
import net.bible.sharedcore.speak.SpeakTransportDialog
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.reading.ChooseSpeakBookmarkDialog
import net.bible.sharedui.reading.SpeakTransportBar
import net.bible.sharedui.speak.AdvancedSpeakSettingsScreen
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

/** Compose host for the advanced Speak settings (classic SpeakSettingsActivity). */
class SpeakSettingsComposeActivity : ActivityBase() {
    private val service: SpeakSettingsService by inject()
    private val transportService: SpeakTransportService by inject()
    private val controller by lazy { AdvancedSpeakSettingsController(service) }

    /**
     * The same transport controller the reading view uses (ComposeReadingViewHost:349) — classic
     * hosts the identical widget at the bottom of this screen (speak_settings.xml:132), which is how
     * speaking is STARTED from here. onConfig is a no-op because the settings cog is hidden on this
     * screen (classic's showConfig default, SpeakTransportWidget.kt:114-118) — this screen IS the
     * config target.
     */
    private val transport by lazy {
        SpeakTransportController(transportService, service, lifecycleScope, onConfig = {})
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val advanced by controller.advanced.collectAsState()
                    val transportState by transport.state.collectAsState()
                    val transportDialog by transport.dialog.collectAsState()
                    AdvancedSpeakSettingsScreen(
                        advanced = advanced,
                        onSynchronize = controller::setSynchronize,
                        onReplaceDivineName = controller::setReplaceDivineName,
                        onAutoBookmark = controller::setAutoBookmark,
                        onRestoreSettingsFromBookmarks = controller::setRestoreSettingsFromBookmarks,
                        onHelp = ::showHelp,
                        transportBar = {
                            SpeakTransportBar(
                                transportState,
                                onPlayPause = { transport.togglePlayPause() },
                                onStop = { transport.stop() },
                                onRewind = { transport.rewind() },
                                onForward = { transport.forward() },
                                onPrev = { transport.prevVerse() },
                                onNext = { transport.nextVerse() },
                                onBookmark = { transport.onBookmarkButton() },
                                onConfig = {},
                                showConfig = false,
                            )
                        },
                        onNavigateUp = { onBackPressedDispatcher.onBackPressed() },
                    )
                    (transportDialog as? SpeakTransportDialog.ChooseSpeakBookmark)?.let { d ->
                        ChooseSpeakBookmarkDialog(
                            rows = d.rows,
                            onChoose = { transport.onSpeakBookmarkChosen(it) },
                            onDismiss = { transport.dismissDialog() },
                        )
                    }
                }
            }
        }
    }

    private fun showHelp() {
        val html = (
            "<b>${getString(R.string.conf_speak_auto_bookmark)}</b><br><br>"
                + "<b><a href=\"$automaticSpeakBookmarkingVideo\">${getString(R.string.watch_tutorial_video)}</a></b><br><br>"
                + getString(R.string.speak_help_auto_bookmark)
                + "<br><br><b>${getString(R.string.conf_save_playback_settings_to_bookmarks)}</b><br><br>"
                + getString(R.string.speak_help_playback_settings)
                + "<br><br>" + getString(R.string.speak_help_playback_settings_example)
            )
        val d = AlertDialog.Builder(this).setMessage(htmlToSpan(html))
            .setPositiveButton(android.R.string.ok) { _, _ -> }.create()
        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }
}
