package net.bible.android.control.versification.mapping;

import static net.bible.android.control.versification.mapping.VersificationConstants.GERMAN_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.KJV_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.SYNODAL_V11N;
import net.bible.android.control.versification.mapping.base.TwoStepVersificationMapping;


/**
 * Map verses between German and Synodal versification via KJV
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class GermanSynodalVersificationMapping extends TwoStepVersificationMapping implements VersificationMapping {
	public GermanSynodalVersificationMapping() {
		super(GERMAN_V11N, KJV_V11N, SYNODAL_V11N);
	}
}