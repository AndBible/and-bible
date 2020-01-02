/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.view.activity;

import net.bible.android.activity.SpeakWidgetManager;
import net.bible.android.control.ApplicationComponent;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.bookmark.BookmarkLabels;
import net.bible.android.view.activity.bookmark.Bookmarks;
import net.bible.android.view.activity.bookmark.ManageLabels;
import net.bible.android.view.activity.comparetranslations.CompareTranslations;
import net.bible.android.view.activity.download.Download;
import net.bible.android.view.activity.download.ProgressStatus;
import net.bible.android.view.activity.footnoteandref.FootnoteAndRefActivity;
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
import net.bible.android.view.activity.speak.GeneralSpeakActivity;
import net.bible.android.view.activity.speak.BibleSpeakActivity;

import dagger.Component;

import net.bible.android.view.activity.speak.SpeakSettingsActivity;
import net.bible.android.view.util.widget.SpeakTransportWidget;
import net.bible.service.device.speak.TextToSpeechNotificationManager;

/**
 * Dagger Component to allow injection of dependencies into activities.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
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

	void inject(GeneralSpeakActivity speak);
	void inject(BibleSpeakActivity speakBible);
	void inject(SpeakSettingsActivity speakSettings);
	void inject(DailyReading dailyReading);
	void inject(DailyReadingList dailyReadingList);
	void inject(ReadingPlanSelectorList readingPlanSelectorList);
	void inject(SearchIndex searchIndex);
	void inject(SpeakTransportWidget w);
	void inject(Search search);
	void inject(SearchResults searchResults);
	void inject(CompareTranslations compareTranslations);
	void inject(FootnoteAndRefActivity footnoteAndRefActivity);
	void inject(MyNotes myNotes);
	void inject(History history);

	// Services
	void inject(TextToSpeechNotificationManager m);
	void inject(SpeakWidgetManager w);

	// progress status screens
	void inject(SearchIndexProgressStatus searchIndexProgressStatus);
	void inject(ProgressStatus progressStatus);
}
