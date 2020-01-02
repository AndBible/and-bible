/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.control.versification.sort;

import net.bible.android.control.versification.ConvertibleVerseRange;
import net.bible.service.common.Logger;

import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

import java.util.List;

/**
 * Find the best compatible versification to use in which 2 verses exist
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

class CompatibleVersificationChooser {

	private final List<Versification> versificationsInOrderOfPreference;

	private final Logger log = new Logger(this.getClass().getName());

	CompatibleVersificationChooser(List<Versification> versificationsInOrderOfPreference) {
		this.versificationsInOrderOfPreference = versificationsInOrderOfPreference;
	}

	Versification findPreferredCompatibleVersification(ConvertibleVerseRange convertibleVerseRange1, ConvertibleVerseRange convertibleVerseRange2) {
		for (Versification v11n : versificationsInOrderOfPreference) {
			if (convertibleVerseRange1.isConvertibleTo(v11n) && convertibleVerseRange2.isConvertibleTo(v11n)) {
				return v11n;
			}
		}

		log.error("Cannot find compatible versification for "+convertibleVerseRange1+" and "+convertibleVerseRange2+".  Returning KJVA.");
		return Versifications.instance().getVersification("KJVA");
	}
}
