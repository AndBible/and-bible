package net.bible.android.view.activity.install_zip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.bible.android.activity.R;

import org.crosswire.common.util.NetUtil;
import org.crosswire.jsword.book.BookDriver;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.sword.SwordBookDriver;
import org.crosswire.jsword.book.sword.SwordBookMetaData;
import org.crosswire.jsword.book.sword.SwordBookPath;
import org.crosswire.jsword.book.sword.SwordConstants;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Install SWORD module from a zip file
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Tuomas Airaksinen [tuomas.airaksinen at gmail dot com]
 */

class ModuleExists extends Exception {
	private static final long serialVersionUID = 1L;
}

class InvalidModule extends Exception {
	private static final long serialVersionUID = 1L;
}

class ZipHandler extends AsyncTask<Void, Integer, Integer> {
	private static final int R_ERROR = 1;
	private static final int R_INVALID_MODULE = 2;
	private static final int R_MODULE_EXISTS = 3;
	private static final int R_OK = 4;

	private Uri uri;
	private InstallZip parent;
	private int total_entries = 0;

	public ZipHandler(Uri _uri, InstallZip _parent) {
		uri = _uri;
		parent = _parent;
	}

	private void checkZipFile() throws IOException, ModuleExists, InvalidModule {
		boolean mods_d_found = false;
		boolean modules_found = false;
		ZipEntry entry;

		File targetDirectory = SwordBookPath.getSwordDownloadDir();

		ZipInputStream zin = new ZipInputStream(parent.getContentResolver()
				.openInputStream(uri));

		while ((entry = zin.getNextEntry()) != null) {
			total_entries++;
			String name = entry.getName();
			File targetFile = new File(targetDirectory, name);
			if (!entry.isDirectory() && targetFile.exists())
				throw new ModuleExists();
			if (name.startsWith(SwordConstants.DIR_CONF + "/")
					&& name.endsWith(SwordConstants.EXTENSION_CONF))
				mods_d_found = true;
			else if (name.startsWith(SwordConstants.DIR_CONF + "/")){}
			else if (name.startsWith(SwordConstants.DIR_DATA + "/"))
				modules_found = true;
			else {
				throw new InvalidModule();
			}
		}

		if (!(mods_d_found && modules_found))
			throw new InvalidModule();

		zin.close();
	}

	private void installZipFile() throws IOException, BookException {
		ZipInputStream zis = new ZipInputStream(parent.getContentResolver()
				.openInputStream(uri));

		ArrayList<File> confFiles = new ArrayList<File>();
		File targetDirectory = SwordBookPath.getSwordDownloadDir();
		try {
			ZipEntry ze;
			int count;
			int entry_num = 0;
			byte[] buffer = new byte[8192];
			while ((ze = zis.getNextEntry()) != null) {

				String name = ze.getName();

				File file = new File(targetDirectory, name);
				if (name.startsWith(SwordConstants.DIR_CONF)
						&& name.endsWith(SwordConstants.EXTENSION_CONF))
					confFiles.add(file);

				File dir = ze.isDirectory() ? file : file.getParentFile();

				if (!dir.isDirectory() && !(dir.mkdirs() || dir.isDirectory()))
					throw new IOException();

				if (ze.isDirectory())
					continue;
				FileOutputStream fout = new FileOutputStream(file);
				try {
					while ((count = zis.read(buffer)) != -1)
						fout.write(buffer, 0, count);
				} finally {
					fout.close();
				}
				publishProgress(++entry_num);
			}
		} finally {
			zis.close();
		}
		// Load configuration files & register books
		BookDriver book_driver = SwordBookDriver.instance();
		for (File confFile : confFiles) {
			String internal = confFile.getName();
			if (internal.endsWith(SwordConstants.EXTENSION_CONF))
				internal = internal.substring(0, internal.length() - 5);
			SwordBookMetaData me = new SwordBookMetaData(confFile, internal,
					NetUtil.getURI(targetDirectory));
			me.setDriver(book_driver);
			SwordBookDriver.registerNewBook(me);
		}

	}

	@Override
	protected Integer doInBackground(Void... params) {
		try {
			checkZipFile();
			installZipFile();
		} catch (IOException | BookException e) {
			Log.e(parent.TAG, "Error occurred", e);
			return R_ERROR;
		} catch (InvalidModule e) {
			return R_INVALID_MODULE;
		} catch (ModuleExists e) {
			return R_MODULE_EXISTS;
		}
		return R_OK;
	}

	@Override
	protected void onPostExecute(Integer result) {
		switch (result) {
		case R_ERROR:
			Toast.makeText(this.parent, R.string.error_occurred,
					Toast.LENGTH_SHORT).show();
			break;
		case R_INVALID_MODULE:
			Toast.makeText(this.parent, R.string.invalid_module,
					Toast.LENGTH_SHORT).show();
			break;
		case R_MODULE_EXISTS:
			Toast.makeText(this.parent, R.string.module_already_installed,
					Toast.LENGTH_SHORT).show();
			break;
		case R_OK:
			Toast.makeText(this.parent, R.string.install_zip_successfull,
					Toast.LENGTH_SHORT).show();
			break;
		}
		parent.finish();
	}

	@Override
	protected void onProgressUpdate(Integer... values) {
		if (values[0] == 1)
			parent.setTitle(R.string.extracting_zip_file);
		int progress_now = (int) Math
				.round(((float) values[0] / (float) total_entries)
						* parent.progressBar.getMax());
		parent.progressBar.setProgress(progress_now);
	}
}

public class InstallZip extends Activity {

	static final String TAG = "InstallZip";
	private static final int PICK_FILE = 1;
	public ProgressBar progressBar;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_install_zip);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		startActivityForResult(intent, PICK_FILE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case PICK_FILE:
			if (resultCode == RESULT_OK) {
				Uri uri = data.getData();
				setTitle(R.string.checking_zip_file);
				ZipHandler zh = new ZipHandler(uri, this);
				zh.execute();
			}
			else if (resultCode == RESULT_CANCELED)
				finish();
			break;
		}
	}
	
	@Override
	public void onBackPressed() {
	}
}
