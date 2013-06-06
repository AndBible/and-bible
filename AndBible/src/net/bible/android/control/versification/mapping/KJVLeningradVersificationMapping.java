package net.bible.android.control.versification.mapping;

import static net.bible.android.control.versification.mapping.VersificationConstants.KJV_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.LENINGRAD_V11N;
import net.bible.android.control.versification.mapping.base.PropertyFileVersificationMapping;

/**
 * Map verses between KJV and Leningrad versification
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class KJVLeningradVersificationMapping extends PropertyFileVersificationMapping {
	public KJVLeningradVersificationMapping() {
		super(KJV_V11N, LENINGRAD_V11N);
	}
}