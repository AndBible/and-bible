package net.bible.android.control;

import net.bible.android.control.backup.BackupControl;
import net.bible.android.control.download.DownloadControl;

import dagger.Component;

/**
 * Dagger Component to expose application scoped dependencies.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@ApplicationScope
@Component(modules=ControllerModule.class)
public interface ControllerComponent {

	//Exposed to sub-graphs.
	BackupControl backupControl();
	DownloadControl downloadControl();

}
