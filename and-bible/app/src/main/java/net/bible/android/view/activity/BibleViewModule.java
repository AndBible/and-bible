package net.bible.android.view.activity;

import net.bible.android.view.activity.page.BibleView;
import net.bible.android.view.activity.page.VerseActionModeMediator;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module to create activity scoped dependencies
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@Module
public class BibleViewModule {

	private BibleView bibleView;

	public BibleViewModule(BibleView bibleView) {
		this.bibleView = bibleView;
	}

	@Provides
	@BibleViewScope
	BibleView provideBibleView() {
		return bibleView;
	}

	@Provides
	@BibleViewScope
	VerseActionModeMediator.VerseHighlightControl provideVerseHighlightControl() {
		return bibleView;
	}

}
