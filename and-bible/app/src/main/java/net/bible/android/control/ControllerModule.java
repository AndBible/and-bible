package net.bible.android.control;

import net.bible.android.control.download.DownloadControl;
import net.bible.android.control.download.DownloadQueue;
import net.bible.service.download.RepoFactory;
import net.bible.service.font.FontControl;

import java.util.concurrent.Executors;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module to create application scoped dependencies
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@ApplicationScope
@Module
public class ControllerModule {

	@Provides
	@ApplicationScope
	public DownloadControl provideDownloadControl() {
		return new DownloadControl(new DownloadQueue(Executors.newSingleThreadExecutor()), RepoFactory.getInstance().getXiphosRepo(), FontControl.getInstance());
	}
}
