package net.bible.android.control.bookmark;

import android.app.Activity;
import android.content.Intent;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;

import de.greenrobot.event.EventBus;
import net.bible.android.activity.R;
import net.bible.android.common.resource.ResourceProvider;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.event.passage.SynchronizeWindowsEvent;
import net.bible.android.control.page.CurrentBiblePage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.control.speak.PlaybackSettings;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.IntentHelper;
import net.bible.android.view.activity.bookmark.BookmarkLabels;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.bookmark.BookmarkDBAdapter;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;
import net.bible.service.sword.SwordContentFacade;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.Versification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class BookmarkControl {

	public static final String BOOKMARK_IDS_EXTRA = "bookmarkIds";
	public static final String LABEL_NO_EXTRA = "labelNo";

	private final LabelDto LABEL_ALL;
	private final LabelDto LABEL_UNLABELLED;

	private static final String BOOKMARK_SORT_ORDER = "BookmarkSortOrder";

	private final SwordContentFacade swordContentFacade;

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private static final String TAG = "BookmarkControl";

	@Inject
	public BookmarkControl(SwordContentFacade swordContentFacade, ActiveWindowPageManagerProvider activeWindowPageManagerProvider, ResourceProvider resourceProvider) {
		this.swordContentFacade = swordContentFacade;
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
		LABEL_ALL = new LabelDto(-999L, resourceProvider.getString(R.string.all), null);
		LABEL_UNLABELLED = new LabelDto(-998L, resourceProvider.getString(R.string.label_unlabelled), null);
	}

	public void updateBookmarkSettings(PlaybackSettings settings) {
		if(activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage().getBookCategory().equals(BookCategory.BIBLE)) {
			updateBookmarkSettings(activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentBible().getSingleKey(), settings);
		}
	}

	public void updateBookmarkSettings(Key key, PlaybackSettings settings) {
		BookmarkDto bookmarkDto = getBookmarkByKey(key);
		if(bookmarkDto != null && bookmarkDto.getPlaybackSettings() != null) {
			bookmarkDto.setPlaybackSettings(settings);
			addOrUpdateBookmark(bookmarkDto);
		}
	}

	public boolean addBookmarkForVerseRange(VerseRange verseRange) {
		boolean bOk = false;
		if (isCurrentDocumentBookmarkable()) {

			BookmarkDto bookmarkDto = getBookmarkByKey(verseRange);
			final Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
			final View currentView = currentActivity.findViewById(android.R.id.content);
			boolean success = false;
			Integer message = null;
			if (bookmarkDto == null)
			{
				// prepare new bookmark and add to db
				bookmarkDto = new BookmarkDto();
				bookmarkDto.setVerseRange(verseRange);
				bookmarkDto = addOrUpdateBookmark(bookmarkDto);

				success = bookmarkDto != null;
				message = R.string.bookmark_added;
			} else
			{
				bookmarkDto = refreshBookmarkDate(bookmarkDto);
				success = bookmarkDto != null;
				message = R.string.bookmark_date_updated;
			}

			final BookmarkDto affectedBookmark = bookmarkDto;
			if (success) {
				// success
				int actionTextColor = CommonUtils.getResourceColor(R.color.snackbar_action_text);
				Snackbar.make(currentView, message, Snackbar.LENGTH_LONG).setActionTextColor(actionTextColor).setAction(R.string.assign_labels, new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						showBookmarkLabelsActivity(currentActivity, affectedBookmark);
					}
				}).show();
				bOk = true;
			} else {
				Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
			}
		}
		EventBus.getDefault().post(new SynchronizeWindowsEvent());
		return bOk;
	}

	public boolean deleteBookmarkForVerseRange(VerseRange verseRange) {
		boolean bOk = false;
		if (isCurrentDocumentBookmarkable()) {

			BookmarkDto bookmarkDto = getBookmarkByKey(verseRange);
			final Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
			final View currentView = currentActivity.findViewById(android.R.id.content);
			if (bookmarkDto !=null) {
				if (deleteBookmark(bookmarkDto)) {
					Snackbar.make(currentView, R.string.bookmark_deleted, Snackbar.LENGTH_SHORT).show();
				} else {
					Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
				}
			}
		}
		EventBus.getDefault().post(new SynchronizeWindowsEvent());
		return bOk;
	}

	// Label related methods
	public void editBookmarkLabelsForVerseRange(VerseRange verseRange) {
		if (isCurrentDocumentBookmarkable()) {
			BookmarkDto bookmarkDto = getBookmarkByKey(verseRange);
			final Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
			if (bookmarkDto != null) {
				// Show label view for new bookmark
				showBookmarkLabelsActivity(currentActivity, bookmarkDto);
			}
		}
	}

	public String getBookmarkVerseKey(BookmarkDto bookmark) {
		String keyText = "";
		try {
			Versification versification = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentBible().getVersification();
			keyText = bookmark.getVerseRange(versification).getName();
		} catch (Exception e) {
			Log.e(TAG, "Error getting verse text", e);
		}
		return keyText;
	}

	public String getBookmarkVerseText(BookmarkDto bookmark) {
		String verseText = "";
		try {
			CurrentBiblePage currentBible = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentBible();
			Versification versification = currentBible.getVersification();
			verseText = swordContentFacade.getPlainText(currentBible.getCurrentDocument(), bookmark.getVerseRange(versification));
			verseText = CommonUtils.limitTextLength(verseText);
		} catch (Exception e) {
			Log.e(TAG, "Error getting verse text", e);
		}
		return verseText;
	}

	// pure bookmark methods

	/** get all bookmarks */
	public List<BookmarkDto> getAllBookmarks() {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		List<BookmarkDto> bookmarkList = null;
		try {
			db.open();
			bookmarkList = db.getAllBookmarks();
			bookmarkList = getSortedBookmarks(bookmarkList);
		} finally {
			db.close();
		}

		return bookmarkList;
	}

	/** create a new bookmark */
	public BookmarkDto addOrUpdateBookmark(BookmarkDto bookmark) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		BookmarkDto newBookmark = null;
		try {
			db.open();
			newBookmark = db.insertOrUpdateBookmark(bookmark);
		} finally {
			db.close();
		}
		return newBookmark;
	}

	/** update bookmark date */
	public BookmarkDto refreshBookmarkDate(BookmarkDto bookmark) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		BookmarkDto updatedBookmark = null;
		try {
			db.open();
			updatedBookmark = db.updateBookmarkDate(bookmark);
		} finally {
			db.close();
		}
		return updatedBookmark;
	}

	/** get all bookmarks */
	public List<BookmarkDto> getBookmarksById(long[] ids) {
		List<BookmarkDto> bookmarks = new ArrayList<>();
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		try {
			db.open();
			for (long id : ids) {
				BookmarkDto bookmark = db.getBookmarkDto(id);
				if (bookmark != null) {
					bookmarks.add(bookmark);
				}
			}
		} finally {
			db.close();
		}

		return bookmarks;
	}

	public boolean isBookmarkForKey(Key key) {
		return key!=null && getBookmarkByKey(key)!=null;
	}

	/** get bookmark with the same start verse as this key if it exists or return null */
	public BookmarkDto getBookmarkByKey(Key key) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		BookmarkDto bookmark = null;
		try {
			db.open();
			bookmark = db.getBookmarkByStartKey(key.getOsisRef());
		} finally {
			db.close();
		}

		return bookmark;
	}

	/** delete this bookmark (and any links to labels) */
	public boolean deleteBookmark(BookmarkDto bookmark) {
		boolean bOk = false;
		if (bookmark!=null && bookmark.getId()!=null) {
			BookmarkDBAdapter db = new BookmarkDBAdapter();
			try {
				db.open();
				bOk = db.removeBookmark(bookmark);
			} finally {
				db.close();
			}
		}
		return bOk;
	}

	/** get bookmarks with the given label */
	public List<BookmarkDto> getBookmarksWithLabel(LabelDto label) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		List<BookmarkDto> bookmarkList = null;
		try {
			db.open();
			if (LABEL_ALL.equals(label)) {
				bookmarkList = db.getAllBookmarks();
			} else if (LABEL_UNLABELLED.equals(label)) {
				bookmarkList = db.getUnlabelledBookmarks();
			} else {
				bookmarkList = db.getBookmarksWithLabel(label);
			}
			assert bookmarkList!=null;
			bookmarkList = getSortedBookmarks(bookmarkList);

		} finally {
			db.close();
		}

		return bookmarkList;
	}


	/** get bookmarks associated labels */
	public List<LabelDto> getBookmarkLabels(BookmarkDto bookmark) {
		List<LabelDto> labels;

		BookmarkDBAdapter db = new BookmarkDBAdapter();
		try {
			db.open();
			labels = db.getBookmarkLabels(bookmark);
		} finally {
			db.close();
		}
		return labels;
	}

	/** label the bookmark with these and only these labels */
	public void setBookmarkLabels(BookmarkDto bookmark, List<LabelDto> labels) {
		// never save LABEL_ALL
		labels.remove(LABEL_ALL);
		labels.remove(LABEL_UNLABELLED);

		BookmarkDBAdapter db = new BookmarkDBAdapter();
		try {
			db.open();
			List<LabelDto> prevLabels = db.getBookmarkLabels(bookmark);

			//find those which have been deleted and remove them
			Set<LabelDto> deleted = new HashSet<>(prevLabels);
			deleted.removeAll(labels);
			for (LabelDto label : deleted) {
				db.removeBookmarkLabelJoin(bookmark, label);
			}

			//find those which are new and persist them
			Set<LabelDto> added = new HashSet<>(labels);
			added.removeAll(prevLabels);
			for (LabelDto label : added) {
				db.insertBookmarkLabelJoin(bookmark, label);
			}

		} finally {
			db.close();
		}
	}

	public LabelDto saveOrUpdateLabel(LabelDto label) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		LabelDto retLabel = null;
		try {
			db.open();
			if (label.getId()==null) {
				retLabel = db.insertLabel(label);
			} else {
				retLabel = db.updateLabel(label);
			}
		} finally {
			db.close();
		}
		return retLabel;
	}

	/** delete this bookmark (and any links to labels) */
	public boolean deleteLabel(LabelDto label) {
		boolean bOk = false;
		if (label!=null && label.getId()!=null && !LABEL_ALL.equals(label) && !LABEL_UNLABELLED.equals(label)) {
			BookmarkDBAdapter db = new BookmarkDBAdapter();
			try {
				db.open();
				bOk = db.removeLabel(label);
			} finally {
				db.close();
			}
		}
		return bOk;
	}

	public List<LabelDto> getAllLabels() {
		List<LabelDto> labelList = getAssignableLabels();

		// add special label that is automatically associated with all-bookmarks
		labelList.add(0, LABEL_UNLABELLED);
		labelList.add(0, LABEL_ALL);

		return labelList;
	}

	public List<LabelDto> getAssignableLabels() {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		List<LabelDto> labelList = new ArrayList<>();
		try {
			db.open();
			labelList.addAll(db.getAllLabels());
		} finally {
			db.close();
		}

		Collections.sort(labelList);
		return labelList;
	}

	public void changeBookmarkSortOrder() {
		if (getBookmarkSortOrder().equals(BookmarkSortOrder.BIBLE_BOOK)) {
			setBookmarkSortOrder(BookmarkSortOrder.DATE_CREATED);
		} else {
			setBookmarkSortOrder(BookmarkSortOrder.BIBLE_BOOK);
		}
	}

	private BookmarkSortOrder getBookmarkSortOrder() {
		String bookmarkSortOrderStr = CommonUtils.getSharedPreference(BOOKMARK_SORT_ORDER, BookmarkSortOrder.BIBLE_BOOK.toString());
		return BookmarkSortOrder.valueOf(bookmarkSortOrderStr);
	}
	private void setBookmarkSortOrder(BookmarkSortOrder bookmarkSortOrder) {
		CommonUtils.saveSharedPreference(BOOKMARK_SORT_ORDER, bookmarkSortOrder.toString());
	}

	public String getBookmarkSortOrderDescription() {
		if (BookmarkSortOrder.BIBLE_BOOK.equals(getBookmarkSortOrder())) {
			return CommonUtils.getResourceString(R.string.sort_by_bible_book);
		} else {
			return CommonUtils.getResourceString(R.string.sort_by_date);
		}
	}

	private List<BookmarkDto> getSortedBookmarks(List<BookmarkDto> bookmarkList) {

		Comparator<BookmarkDto> comparator;
		switch (getBookmarkSortOrder()) {
			case DATE_CREATED:
				comparator = new BookmarkCreationDateComparator();
				break;
			case BIBLE_BOOK:
			default:
				comparator = new BookmarkDtoBibleOrderComparator(bookmarkList);
				break;
		}

		// the new Java 7 sort is stricter and occasionally generates errors, so prevent total crash on listing bookmarks
		try {
			Collections.sort(bookmarkList, comparator);
		} catch (Exception e) {
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
		}

		return bookmarkList;
	}

	private boolean isCurrentDocumentBookmarkable() {
		CurrentPageManager currentPageControl = activeWindowPageManagerProvider.getActiveWindowPageManager();
		return currentPageControl.isBibleShown() || currentPageControl.isCommentaryShown();
	}

	private void showBookmarkLabelsActivity(Activity currentActivity, BookmarkDto bookmarkDto) {
		// Show label view for new bookmark
		final Intent intent = new Intent(currentActivity, BookmarkLabels.class);
		intent.putExtra(BOOKMARK_IDS_EXTRA, new long[] {bookmarkDto.getId()});
		currentActivity.startActivityForResult(intent, IntentHelper.REFRESH_DISPLAY_ON_FINISH);
	}
}
