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
package net.bible.android.control.versification

import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import org.crosswire.jsword.versification.BibleBook
import net.bible.android.control.versification.Scripture
import java.util.ArrayList

/**
 * Enable separation of Scripture books
 * Not complete because dc fragments are sometimes embedded within books like Esther and Daniel
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object Scripture {
    private val SCRIPTURAL_V11N = Versifications.instance().getVersification("KJV")
    private val INTROS: MutableList<BibleBook> = ArrayList()

    init {
        INTROS.add(BibleBook.INTRO_BIBLE)
        INTROS.add(BibleBook.INTRO_OT)
        INTROS.add(BibleBook.INTRO_NT)
    }

    /** TODO: needs to be improved because some books contain extra chapters which are non-scriptural
     */
    fun isScripture(bibleBook: BibleBook): Boolean {
        return SCRIPTURAL_V11N.containsBook(bibleBook) && !INTROS.contains(bibleBook)
    }

    fun isIntro(bibleBook: BibleBook): Boolean {
        return INTROS.contains(bibleBook)
    }
}
