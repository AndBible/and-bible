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
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
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
