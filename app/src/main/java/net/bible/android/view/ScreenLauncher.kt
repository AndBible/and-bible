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
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.activity.navigation.ChooseDictionaryWord
import net.bible.android.view.activity.navigation.ChooseDictionaryWordComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKey
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKeyComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKey
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKeyComposeActivity
import net.bible.android.view.activity.navigation.History
import net.bible.android.view.activity.navigation.HistoryComposeActivity
import net.bible.android.view.activity.readingplan.DailyReading
import net.bible.android.view.activity.readingplan.DailyReadingComposeActivity
import net.bible.android.view.activity.readingplan.DailyReadingList
import net.bible.android.view.activity.readingplan.DailyReadingListComposeActivity
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorComposeActivity
import net.bible.android.view.activity.search.SearchIndexProgressComposeActivity
import net.bible.android.view.activity.search.SearchIndexProgressStatus
import net.bible.service.common.CommonUtils

/** Screens that have both a classic (XML) and a new (Compose) implementation. */
enum class Screen { Calculator, History, SearchIndexProgress, ReadingPlanSelector, DailyReadingList, ReadingPlan, ChooseGeneralBookKey, ChooseMapKey, ChooseDictionaryWord }

/**
 * Central old/new routing indirection (Strangler Fig). Chooses the classic or Compose
 * implementation per screen from the global `use_compose_ui` debug flag. This is the seed of
 * the future CMP navigation graph (Batch Z). Task 11 attached the Compose calculator host.
 */
object ScreenLauncher {
    fun useComposeFor(@Suppress("UNUSED_PARAMETER") screen: Screen): Boolean =
        CommonUtils.settings.getBoolean("use_compose_ui", false)

    /** The Activity class implementing [screen] under the current `use_compose_ui` flag. */
    fun targetFor(screen: Screen): Class<*> = when (screen) {
        Screen.Calculator ->
            if (useComposeFor(screen)) CalculatorComposeActivity::class.java
            else CalculatorActivity::class.java
        Screen.History ->
            if (useComposeFor(screen)) HistoryComposeActivity::class.java
            else History::class.java
        Screen.SearchIndexProgress ->
            if (useComposeFor(screen)) SearchIndexProgressComposeActivity::class.java
            else SearchIndexProgressStatus::class.java
        Screen.ReadingPlanSelector ->
            if (useComposeFor(screen)) ReadingPlanSelectorComposeActivity::class.java
            else ReadingPlanSelectorList::class.java
        Screen.DailyReadingList ->
            if (useComposeFor(screen)) DailyReadingListComposeActivity::class.java
            else DailyReadingList::class.java
        Screen.ReadingPlan ->
            if (useComposeFor(screen)) DailyReadingComposeActivity::class.java
            else DailyReading::class.java
        Screen.ChooseGeneralBookKey ->
            if (useComposeFor(screen)) ChooseGeneralBookKeyComposeActivity::class.java
            else ChooseGeneralBookKey::class.java
        Screen.ChooseMapKey ->
            if (useComposeFor(screen)) ChooseMapKeyComposeActivity::class.java
            else ChooseMapKey::class.java
        Screen.ChooseDictionaryWord ->
            if (useComposeFor(screen)) ChooseDictionaryWordComposeActivity::class.java
            else ChooseDictionaryWord::class.java
    }

    /**
     * Intent for [screen], routed old/new. Callers that need the result (the calculator's PIN
     * unlock uses `startActivityForResult` / `awaitIntent`) build on this instead of [open].
     */
    fun intentFor(context: Context, screen: Screen): Intent = Intent(context, targetFor(screen))

    fun open(context: Context, screen: Screen) {
        context.startActivity(intentFor(context, screen))
    }
}
