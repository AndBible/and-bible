package net.bible.android.view.activity;

import net.bible.android.control.ApplicationComponent;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.bookmark.BookmarkLabels;
import net.bible.android.view.activity.bookmark.Bookmarks;
import net.bible.android.view.activity.bookmark.ManageLabels;
import net.bible.android.view.activity.comparetranslations.CompareTranslations;
import net.bible.android.view.activity.download.Download;
import net.bible.android.view.activity.download.DownloadStatus;
import net.bible.android.view.activity.download.ProgressStatus;
import net.bible.android.view.activity.footnoteandref.FootnoteAndRefActivity;
import net.bible.android.view.activity.help.Help;
import net.bible.android.view.activity.mynote.MyNotes;
import net.bible.android.view.activity.navigation.ChooseDictionaryWord;
import net.bible.android.view.activity.navigation.ChooseDocument;
import net.bible.android.view.activity.navigation.GridChoosePassageBook;
import net.bible.android.view.activity.navigation.GridChoosePassageChapter;
import net.bible.android.view.activity.navigation.GridChoosePassageVerse;
import net.bible.android.view.activity.navigation.History;
import net.bible.android.view.activity.navigation.genbookmap.ChooseKeyBase;
import net.bible.android.view.activity.readingplan.DailyReading;
import net.bible.android.view.activity.readingplan.DailyReadingList;
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList;
import net.bible.android.view.activity.search.Search;
import net.bible.android.view.activity.search.SearchIndex;
import net.bible.android.view.activity.search.SearchIndexProgressStatus;
import net.bible.android.view.activity.search.SearchResults;
import net.bible.android.view.activity.speak.Speak;

import dagger.Component;

/**
 * Dagger Component to allow injection of dependencies into activities.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@ActivityScope
@Component(dependencies = {ApplicationComponent.class} )
public interface ActivityComponent {
	// Activities that are permitted to be injected

	// don't like this but inject is called from ActivityBase and the subclasses
	void inject(ActivityBase activityBase);

	void inject(StartupActivity startupActivity);

	void inject(Bookmarks bookmarks);
	void inject(BookmarkLabels bookmarkLabels);
	void inject(ManageLabels manageLabels);

	void inject(GridChoosePassageBook gridChoosePassageBook);
	void inject(GridChoosePassageChapter gridChoosePassageChapter);
	void inject(GridChoosePassageVerse gridChoosePassageVerse);
	void inject(ChooseDictionaryWord chooseDictionaryWord);
	void inject(ChooseKeyBase chooseKeyBase);

	void inject(ChooseDocument chooseDocument);
	void inject(Download download);

	void inject(Speak speak);
	void inject(DailyReading dailyReading);
	void inject(DailyReadingList dailyReadingList);
	void inject(ReadingPlanSelectorList readingPlanSelectorList);
	void inject(SearchIndex searchIndex);
	void inject(Search search);
	void inject(SearchResults searchResults);
	void inject(CompareTranslations compareTranslations);
	void inject(FootnoteAndRefActivity footnoteAndRefActivity);
	void inject(MyNotes myNotes);
	void inject(History history);
	void inject(Help help);

	// progress status screens
	void inject(SearchIndexProgressStatus searchIndexProgressStatus);
	void inject(DownloadStatus downloadStatus);
	void inject(ProgressStatus progressStatus);
}
