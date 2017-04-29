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
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
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
