package net.bible.android.control;

import net.bible.android.control.bookmark.Bookmark;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.search.SearchControl;

//TODO replace with ioc (maybe)
/** allow access to control layer - would like to 
 *
 * @author denha1m
 *
 */
public class ControlFactory {
	//TODO move instance creation here
	private CurrentPageManager currentPageManager = CurrentPageManager.getInstance();
	private DocumentControl documentControl = new DocumentControl();
	private PageControl pageControl = new PageControl();
	private SearchControl searchControl = new SearchControl();
	private Bookmark bookmarkControl = new BookmarkControl();
	
	private static ControlFactory singleton = new ControlFactory();
	
	public static ControlFactory getInstance() {
		return singleton;
	}
	
	public DocumentControl getDocumentControl() {
		return documentControl;		
	}

	public PageControl getPageControl() {
		return pageControl;		
	}

	public SearchControl getSearchControl() {
		return searchControl;		
	}

	public CurrentPageManager getCurrentPageControl() {
		return currentPageManager;		
	}

	/**
	 * @return the bookmarkControl
	 */
	public Bookmark getBookmarkControl() {
		return bookmarkControl;
	}
}
