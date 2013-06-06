package net.bible.android.control.versification.mapping;

import static net.bible.android.control.versification.mapping.VersificationConstants.KJV_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.LENINGRAD_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.VULG_V11N;
import net.bible.android.control.versification.mapping.base.TwoStepVersificationMapping;


/**
 * Map verses between German and Synodal versification via KJV
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class LeningradVulgVersificationMapping extends TwoStepVersificationMapping implements VersificationMapping {
	public LeningradVulgVersificationMapping() {
		super(LENINGRAD_V11N, KJV_V11N, VULG_V11N);
	}
}