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

package net.bible.android.control;

import net.bible.android.control.backup.BackupControl;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.comparetranslations.CompareTranslationsControl;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.control.footnoteandref.FootnoteAndRefControl;
import net.bible.android.control.footnoteandref.NoteDetailCreator;
import net.bible.android.control.link.LinkControl;
import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.control.navigation.DocumentBibleBooksFactory;
import net.bible.android.control.navigation.NavigationControl;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.page.PageTiltScrollControlFactory;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.control.report.ErrorReportControl;
import net.bible.android.control.search.SearchControl;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.control.versification.BibleTraverser;
import net.bible.android.view.activity.navigation.biblebookactionbar.BibleBookActionBarManager;
import net.bible.android.view.activity.navigation.biblebookactionbar.SortActionBarButton;
import net.bible.android.view.activity.page.BibleKeyHandler;
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanActionBarManager;
import net.bible.android.view.activity.search.searchresultsactionbar.ScriptureToggleActionBarButton;
import net.bible.android.view.activity.search.searchresultsactionbar.SearchResultsActionBarManager;
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton;
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakStopActionBarButton;
import net.bible.service.history.HistoryManager;
import net.bible.service.history.HistoryTraversalFactory;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import dagger.Component;

/**
 * Dagger Component to expose application scoped dependencies.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
@Component(modules=ApplicationModule.class)
public interface ApplicationComponent {

	//Exposed to sub-graphs.
	WarmUp warmUp();
	ErrorReportControl errorReportControl();

	SwordDocumentFacade swordDocumentFacade();
	SwordContentFacade swordContentFacade();
	BibleTraverser bibleTraverser();
	NavigationControl navigationControl();
	DocumentBibleBooksFactory documentBibleBooksFactory();
	WindowControl windowControl();
	ActiveWindowPageManagerProvider activeWindowPageManagerProvider();
	LinkControl linkControl();
	PageTiltScrollControlFactory pageTiltScrollControlFactory();
	HistoryManager historyManager();
	HistoryTraversalFactory historyTraversalFactory();
	BibleKeyHandler bibleKeyHandler();

	DocumentControl documentControl();
	BackupControl backupControl();
	BookmarkControl bookmarkControl();
	MyNoteControl myNoteControl();
	NoteDetailCreator noteDetailCreator();
	DownloadControl downloadControl();
	PageControl pageControl();
	ReadingPlanControl readingPlanControl();
	SearchControl searchControl();
	CompareTranslationsControl compareTranslationsControl();
	FootnoteAndRefControl footnoteAndRefControl();

	SpeakControl speakControl();

	SortActionBarButton sortActionBarButton();
	SpeakActionBarButton speakActionBarButton();
	SpeakStopActionBarButton speakStopActionBarButton();
	ScriptureToggleActionBarButton scriptureToggleActionBarButton();
	ReadingPlanActionBarManager readingPlanActionBarManager();
	SearchResultsActionBarManager searchResultsActionBarManager();
	BibleBookActionBarManager bibleBookActionBarManager();
}
