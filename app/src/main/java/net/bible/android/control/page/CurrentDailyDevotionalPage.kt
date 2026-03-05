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
package net.bible.android.control.page

import net.bible.android.control.PassageChangeMediator
import net.bible.android.view.activity.base.ActivityBase
import org.crosswire.jsword.passage.PreferredKey
import java.util.Calendar

/** Daily devotional page — navigates by date key (e.g. MM.DD).
 *  Extends CurrentDictionaryPage since SWORD uses the same zLD driver.
 */
class CurrentDailyDevotionalPage internal constructor(
    pageManager: CurrentPageManager
) : CurrentDictionaryPage(pageManager) {

    override val documentCategory = DocumentCategory.DAILY_DEVOTIONS

    override fun startKeyChooser(context: ActivityBase) {
        android.util.Log.d("DailyDevotional", "startKeyChooser called - showing DatePickerDialog")
        val cal = Calendar.getInstance()
        // Pré-remplir avec la date actuelle de la clé si disponible
        key?.let { k ->
            try {
                val parts = k.name.split(".")
                if (parts.size == 2) {
                    cal.set(Calendar.MONTH, parts[0].toInt() - 1)
                    cal.set(Calendar.DAY_OF_MONTH, parts[1].toInt())
                }
            } catch (e: Exception) { /* utiliser la date du jour */ }
        }

        android.app.DatePickerDialog(
            context,
            { _, _, month, day ->
                val keyStr = String.format("%02d.%02d", month + 1, day)
                currentDocument?.let { doc ->
                    try {
                        setKey(doc.getKey(keyStr))
                        PassageChangeMediator.onCurrentPageChanged(pageManager.window)
                    } catch (e: Exception) {
                        // clé non trouvée dans ce module
                    }
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            // Cacher l'année — les devotionals sont indépendants de l'année
            datePicker.findViewById<android.view.View>(
                context.resources.getIdentifier("year", "id", "android")
            )?.visibility = android.view.View.GONE
        }.show()
    }

    /** Set key to today's date using JSword's PreferredKey interface */
    fun setToToday() {
        currentDocument?.let { doc ->
            try {
                if (doc is PreferredKey) {
                    setKey(doc.preferred)
                } else {
                    cachedGlobalKeyList?.firstOrNull()?.let { setKey(it) }
                }
            } catch (e: Exception) {
                cachedGlobalKeyList?.firstOrNull()?.let { setKey(it) }
            }
        }
    }

    companion object {
        private const val TAG = "CurrentDailyDevotionalPage"
    }
}
