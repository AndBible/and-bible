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

package net.bible.android.view

import android.content.Context
import android.content.Intent
import net.bible.android.view.activity.discrete.CalculatorActivity
import net.bible.service.common.CommonUtils

/** Screens that have both a classic (XML) and a new (Compose) implementation. */
enum class Screen { Calculator }

/**
 * Central old/new routing indirection (Strangler Fig). Chooses the classic or Compose
 * implementation per screen from the global `use_compose_ui` debug flag. This is the seed of
 * the future CMP navigation graph (Batch Z). Task 11 attaches the Compose calculator host.
 */
object ScreenLauncher {
    fun useComposeFor(@Suppress("UNUSED_PARAMETER") screen: Screen): Boolean =
        CommonUtils.settings.getBoolean("use_compose_ui", false)

    fun open(context: Context, screen: Screen) {
        val target = when (screen) {
            // Both branches point at the classic Activity until Task 11 wires the Compose host.
            Screen.Calculator -> CalculatorActivity::class.java
        }
        context.startActivity(Intent(context, target))
    }
}
