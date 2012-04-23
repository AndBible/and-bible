package net.bible.service.db;

import java.io.File;

import net.bible.android.BibleApplication;
import net.bible.android.SharedConstants;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/** Allow database to be placed on SD card
 * 
 * Can specify folders for databases in Android 2.2+ but in 2.1 the Context must be altered or SQLLiteOpenHelper throws the error database "contains a path separator"
 * see http://stackoverflow.com/questions/5332328/sqliteopenhelper-problem-with-fully-qualified-db-path-name
 * 
 * @author denha1m
 */
class SDCardDatabaseContext extends ContextWrapper {

	private static final String TAG = "DatabaseContext";

	public SDCardDatabaseContext() {
		super(BibleApplication.getApplication().getApplicationContext());
	}

	@Override
	public File getDatabasePath(String name) {
		String dbfile = SharedConstants.DB_DIR.getAbsolutePath() + File.separator + name;
		if (!dbfile.endsWith(".db")) {
			dbfile += ".db";
		}

		File result = new File(dbfile);

		if (!result.getParentFile().exists()) {
			result.getParentFile().mkdirs();
		}

		Log.w(TAG, "getDatabasePath(" + name + ") = " + result.getAbsolutePath());

		return result;
	}

	@Override
	public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
		SQLiteDatabase result = SQLiteDatabase.openOrCreateDatabase(
				getDatabasePath(name), null);
		// SQLiteDatabase result = super.openOrCreateDatabase(name, mode,
		// factory);
		Log.w(TAG, "openOrCreateDatabase(" + name + ",,) = " + result.getPath());

		return result;
	}
}