package net.bible.android.control.versification.mapping;

import static net.bible.android.control.versification.mapping.VersificationConstants.KJV_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.NRSV_V11N;
import net.bible.android.control.versification.mapping.base.PropertyFileVersificationMapping;

/**
 * Map verses between KJV and Synodal versification
 * 
 * @see: https://code.google.com/p/xulsword/source/browse/trunk/Cpp/src/include/versemaps.h
 * @see: https://gitorious.org/~kalemas/sword-svn-mirrors/kalemas_at_mail_ru-trunk/blobs/35a3fc6bde1ccff945d51558d7e21ab1074a4152/include/canon_synodal.h
 * @see: https://gitorious.org/sword-svn-mirrors/trunk/trees/master/include
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class NRSVKJVVersificationMapping extends PropertyFileVersificationMapping {

	public NRSVKJVVersificationMapping() {
		super(NRSV_V11N, KJV_V11N);
	}
}
