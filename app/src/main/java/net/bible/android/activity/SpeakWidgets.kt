/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.activity

import net.bible.android.BibleApplication
import net.bible.android.view.activity.page.application
import net.bible.android.view.widget.ListeningListGlanceWidget
import net.bible.android.view.widget.SpeakGlanceWidget
import net.bible.android.view.widget.SpeakTextGlanceWidget
import net.bible.android.view.widget.WidgetUpdater
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Lifecycle shell of the home-screen widgets. The widgets themselves are Glance widgets (`view/widget`); this class
 * starts the speak-state producer that keeps them current ([WidgetUpdater]) and holds the five provider classes
 * of the earlier widgets under their old names, so widgets placed before the redesign keep working (they render the
 * new Speak, Speak with text and Listening list widgets they map to).
 */
class SpeakWidgetManager {
    companion object {
        var instance: SpeakWidgetManager? = null
        const val TAG = "SpeakWidget"
    }

    private val app = BibleApplication.application

    init {
        if (instance != null) {
            throw IllegalStateException("This is singleton!")
        }
        instance = this
        WidgetUpdater.start(app)
    }

    /** Re-draws the widget texts from the current resources (called when the app language changes). */
    fun refreshTexts() = WidgetUpdater.refreshForLocale(app)

    fun destroy() {
        WidgetUpdater.stop()
        instance = null
    }

    /** The old Small widget; renders the Speak widget. Hidden from the widget picker. */
    class SmallSpeakControlWidget : GlanceAppWidgetReceiver() {
        override val glanceAppWidget: GlanceAppWidget = SpeakGlanceWidget()
    }

    /** The old Middle widget; renders the Speak widget. Hidden from the widget picker. */
    class MiddleSpeakControlWidget : GlanceAppWidgetReceiver() {
        override val glanceAppWidget: GlanceAppWidget = SpeakGlanceWidget()
    }

    /** The Speak widget (the old Large widget's class). */
    class LargeSpeakControlWidget : GlanceAppWidgetReceiver() {
        override val glanceAppWidget: GlanceAppWidget = SpeakGlanceWidget()
    }

    /** The Speak with text widget (the old Text widget's class). */
    class SpeakTextControlWidget : GlanceAppWidgetReceiver() {
        override val glanceAppWidget: GlanceAppWidget = SpeakTextGlanceWidget()
    }

    /** The Listening list widget (the old speak bookmarks widget's class). */
    class SpeakBookmarkWidget : GlanceAppWidgetReceiver() {
        override val glanceAppWidget: GlanceAppWidget = ListeningListGlanceWidget()
    }
}
