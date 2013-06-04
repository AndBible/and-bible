package net.bible.android.control.versification.mapping;

import net.bible.android.control.versification.mapping.base.PropertyFileVersificationMapping;

/**
 * Map verses between KJV and Leningrad versification
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class KJVGermanVersificationMapping extends PropertyFileVersificationMapping {
	public KJVGermanVersificationMapping() {
		super("KJV", "German");
	}
}