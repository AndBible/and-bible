/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.activity.page;

import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.link.LinkControl;
import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.page.PageTiltScrollControl;
import net.bible.android.control.page.PageTiltScrollControlFactory;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.view.activity.MainBibleActivityScope;
import net.bible.android.view.activity.page.actionmode.VerseActionModeMediator;
import net.bible.android.view.activity.page.actionmode.VerseMenuCommandHandler;

import java.util.Map;
import java.util.WeakHashMap;

import javax.inject.Inject;

/**
 * Build a new BibleView WebView for a Window
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
public class BibleViewFactory {

	private final MainBibleActivity mainBibleActivity;

	private final PageControl pageControl;

	private final PageTiltScrollControlFactory pageTiltScrollControlFactory;

	private final WindowControl windowControl;

	private final BibleKeyHandler bibleKeyHandler;

	private final LinkControl linkControl;

	private final BookmarkControl bookmarkControl;

	private final MyNoteControl myNoteControl;

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private Map<Window, BibleView> screenBibleViewMap;

	private static final int BIBLE_WEB_VIEW_ID_BASE = 990;

	@Inject
	public BibleViewFactory(MainBibleActivity mainBibleActivity, PageControl pageControl, PageTiltScrollControlFactory pageTiltScrollControlFactory, WindowControl windowControl, BibleKeyHandler bibleKeyHandler, LinkControl linkControl, BookmarkControl bookmarkControl, MyNoteControl myNoteControl, ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.mainBibleActivity = mainBibleActivity;
		this.pageControl = pageControl;
		this.pageTiltScrollControlFactory = pageTiltScrollControlFactory;
		this.windowControl = windowControl;
		this.bibleKeyHandler = bibleKeyHandler;
		this.linkControl = linkControl;
		this.bookmarkControl = bookmarkControl;
		this.myNoteControl = myNoteControl;
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;

		screenBibleViewMap = new WeakHashMap<>();
	}

	public BibleView createBibleView(Window window) {
		BibleView bibleView = screenBibleViewMap.get(window);
		if (bibleView==null) {
			final PageTiltScrollControl pageTiltScrollControl = pageTiltScrollControlFactory.getPageTiltScrollControl(window);
			bibleView = new BibleView(this.mainBibleActivity, window, windowControl, bibleKeyHandler, pageControl, pageTiltScrollControl, linkControl);

			VerseActionModeMediator bibleViewVerseActionModeMediator = new VerseActionModeMediator(mainBibleActivity, bibleView, pageControl, new VerseMenuCommandHandler(mainBibleActivity, pageControl, bookmarkControl, myNoteControl), bookmarkControl);

			BibleInfiniteScrollPopulator bibleInfiniteScrollPopulator = new BibleInfiniteScrollPopulator(bibleView, window.getPageManager());

			VerseCalculator verseCalculator = new VerseCalculator();
			BibleJavascriptInterface bibleJavascriptInterface = new BibleJavascriptInterface(bibleViewVerseActionModeMediator, windowControl, verseCalculator, window.getPageManager(), bibleInfiniteScrollPopulator);
			bibleView.setBibleJavascriptInterface(bibleJavascriptInterface);
			bibleView.setId(BIBLE_WEB_VIEW_ID_BASE+window.getScreenNo());
			bibleView.initialise();

			screenBibleViewMap.put(window, bibleView);
		}
		return bibleView;
	}
}
