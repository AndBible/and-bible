package net.bible.android.view.activity;

import net.bible.android.view.activity.page.MainBibleActivity;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module to create MainBibleActivity related dependencies
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@Module
public class MainBibleActivityModule {

	private final MainBibleActivity mainBibleActivity;

	public MainBibleActivityModule(MainBibleActivity mainBibleActivity) {
		this.mainBibleActivity = mainBibleActivity;
	}

	@Provides @MainBibleActivityScope
	MainBibleActivity provideMainBibleActivity() {
		return mainBibleActivity;
	}
}
