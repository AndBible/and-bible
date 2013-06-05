package net.bible.android.control.versification.mapping;

import net.bible.android.control.versification.mapping.base.TwoStepVersificationMapping;


/**
 * Map verses between German and Synodal versification via KJV
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SynodalVulgVersificationMapping extends TwoStepVersificationMapping implements VersificationMapping {
	public SynodalVulgVersificationMapping() {
		super("Synodal", "KJV", "Vulg");
	}
}