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

import org.crosswire.jsword.versification.Versification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Using the list of preferred v11ns calculated from the total list of verses passed in
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

class VersificationPrioritiser {

	private List<Versification> prioritisedVersifications;

	VersificationPrioritiser(List<? extends ConvertibleVerseRangeUser> convertibleVerseRangeUsers) {
		this.prioritisedVersifications = prioritiseVersifications(getVersifications(convertibleVerseRangeUsers));
	}

	private List<Versification> getVersifications(List<? extends ConvertibleVerseRangeUser> convertibleVerseRangeUsers) {
		List<Versification> versifications = new ArrayList<>();
		for (ConvertibleVerseRangeUser cvru : convertibleVerseRangeUsers) {
			versifications.add(cvru.getConvertibleVerseRange().getOriginalVersification());
		}

		return versifications;
	}

	private List<Versification> prioritiseVersifications(List<Versification> versifications) {
		Map<Versification, Integer> map = new HashMap<>();

		// count the occurrences of each versification
		for (Versification versification : versifications) {
			Integer count = map.get(versification);
			map.put(versification, (count == null) ? 1 : count + 1);
		}

		// sort by occurrences
		final List<Map.Entry<Versification, Integer>> entries = new ArrayList<>(map.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<Versification, Integer>>() {
			@Override
			public int compare(Map.Entry<Versification, Integer> o1, Map.Entry<Versification, Integer> o2) {
				return o2.getValue()-o1.getValue();
			}
		});

		// extract v11ns
		List<Versification> sortedVersifications = new ArrayList<>();
		for (Map.Entry<Versification, Integer> entry : entries) {
			sortedVersifications.add(entry.getKey());
		}

		return sortedVersifications;
	}

	List<Versification> getPrioritisedVersifications() {
		return Collections.unmodifiableList(prioritisedVersifications);
	}
}
