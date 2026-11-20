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
package net.bible.android.view.widget

import android.content.Context
import net.bible.android.control.widget.VerseChangeFrequency
import net.bible.android.control.widget.VerseSource
import net.bible.android.control.widget.VerseWidgetConfig
import net.bible.android.database.IdType
import org.json.JSONObject

/** The configuration of each placed Verse widget, in a small preferences file keyed by app widget id. */
object VerseWidgetConfigStore {
    private const val PREFS = "verse_widget_configs"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The saved configuration, or null when the widget has none (or it cannot be read). */
    fun load(context: Context, appWidgetId: Int): VerseWidgetConfig? =
        prefs(context).getString(appWidgetId.toString(), null)?.let { runCatching { decode(it) }.getOrNull() }

    fun save(context: Context, appWidgetId: Int, config: VerseWidgetConfig) {
        prefs(context).edit().putString(appWidgetId.toString(), encode(config)).apply()
    }

    /** Moves the widget on to another verse of its period: shuffle count + 1. Returns the saved configuration. */
    fun shuffle(context: Context, appWidgetId: Int): VerseWidgetConfig? =
        load(context, appWidgetId)?.let { it.copy(shuffleCount = it.shuffleCount + 1) }?.also { save(context, appWidgetId, it) }

    fun delete(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(appWidgetId.toString()).apply()
    }

    internal fun encode(c: VerseWidgetConfig): String = JSONObject().apply {
        when (val s = c.source) {
            is VerseSource.Label -> { put("source", "label"); put("label", s.labelId.toString()) }
            is VerseSource.Random -> { put("source", "random"); put("part", s.part.name) }
            VerseSource.ReadingPlan -> put("source", "plan")
        }
        put("frequency", c.frequency.name)
        c.bibleInitials?.let { put("bible", it) }
        put("showButtons", c.showButtons)
        put("showSource", c.showSourceAndDate)
        put("seed", c.seed)
        put("shuffle", c.shuffleCount)
    }.toString()

    internal fun decode(json: String): VerseWidgetConfig {
        val o = JSONObject(json)
        val source = when (o.getString("source")) {
            "label" -> VerseSource.Label(IdType(o.getString("label")))
            "random" -> VerseSource.Random(VerseSource.RandomPart.valueOf(o.getString("part")))
            "plan" -> VerseSource.ReadingPlan
            else -> error("unknown source")
        }
        return VerseWidgetConfig(
            source = source,
            frequency = VerseChangeFrequency.valueOf(o.getString("frequency")),
            bibleInitials = if (o.has("bible")) o.getString("bible") else null,
            showButtons = o.optBoolean("showButtons", true),
            showSourceAndDate = o.optBoolean("showSource", true),
            seed = o.getLong("seed"),
            shuffleCount = o.optInt("shuffle", 0),
        )
    }
}
