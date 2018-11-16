package net.bible.android.control;

import net.bible.android.common.resource.AndroidResourceProvider;
import net.bible.android.common.resource.ResourceProvider;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.control.download.DownloadQueue;
import net.bible.android.control.email.Emailer;
import net.bible.android.control.email.EmailerImpl;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.control.page.window.WindowControl;
import net.bible.service.download.RepoFactory;
import net.bible.service.font.FontControl;
import net.bible.service.sword.SwordDocumentFacade;

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
@Module
public class ApplicationModule {

	@Provides
	@ApplicationScope
	public DownloadControl provideDownloadControl(SwordDocumentFacade swordDocumentFacade, RepoFactory repoFactory) {
		return new DownloadControl(new DownloadQueue(Executors.newSingleThreadExecutor(), repoFactory), repoFactory, FontControl.getInstance(), swordDocumentFacade);
	}

	@Provides
	@ApplicationScope
	public ActiveWindowPageManagerProvider provideActiveWindowPageManagerProvider(WindowControl windowControl) {
		return windowControl;
	}

	@Provides
	@ApplicationScope
	public ResourceProvider provideResourceProvider(AndroidResourceProvider androidResourceProvider) {
		return androidResourceProvider;
	}

	@Provides
	@ApplicationScope
	public EventManager eventManagerProvider() {
		return ABEventBus.getDefault();
	}

	@Provides
	@ApplicationScope
	public Emailer emailer(EmailerImpl emailerImpl) {
		return emailerImpl;
	}
}
