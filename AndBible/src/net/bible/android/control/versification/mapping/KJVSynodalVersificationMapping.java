package net.bible.android.control.versification.mapping;

/**
 * Map verses between KJV and Synodal versification
 * 
 * @see: https://gitorious.org/~kalemas/sword-svn-mirrors/kalemas_at_mail_ru-trunk/blobs/35a3fc6bde1ccff945d51558d7e21ab1074a4152/include/canon_synodal.h
 * @see: https://code.google.com/p/xulsword/source/browse/trunk/Cpp/src/include/versemaps.h
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class KJVSynodalVersificationMapping extends PropertyFileVersificationMapping {

	public KJVSynodalVersificationMapping() {
		super("KJV", "Synodal");
	}
}
