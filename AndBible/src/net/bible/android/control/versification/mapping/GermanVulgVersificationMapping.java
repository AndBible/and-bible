package net.bible.android.control.versification.mapping;

import net.bible.android.control.versification.mapping.base.TwoStepVersificationMapping;


/**
 * Map verses between German and Synodal versification via KJV
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class GermanVulgVersificationMapping extends TwoStepVersificationMapping implements VersificationMapping {
	public GermanVulgVersificationMapping() {
		super("German", "KJV", "Vulg");
	}
}