package net.bible.android.control;

import net.bible.android.control.backup.BackupControl;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@Module
public class ControllerModule {

	@Provides
	@Singleton
	public BackupControl provideBackupControl() {
		return new BackupControl();
	}
}
