package net.bible.android.control.download;

import android.app.Activity;
import android.view.LayoutInflater;
import android.widget.ProgressBar;

import net.bible.android.activity.R;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.documentdownload.DocumentDownloadEvent;
import net.bible.android.view.activity.download.DocumentDownloadListItem;
import net.bible.service.download.FakeSwordBookFactory;

import net.bible.test.DatabaseResetter;
import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={28})
public class DocumentDownloadProgressCacheTest {

	private DocumentDownloadProgressCache documentDownloadProgressCache;

	private TestData testData;

	@Before
	public void setUp() throws Exception {
		documentDownloadProgressCache = new DocumentDownloadProgressCache();
		testData = new TestData();
	}

	@Test
	public void sendEventOnProgress() throws InterruptedException {

		EventReceiver eventReceiver = new EventReceiver();
		ABEventBus.getDefault().register(eventReceiver);

		documentDownloadProgressCache.startMonitoringDownloads();

		testData.progress.setWorkDone(30);

		Thread.sleep(10);
		assertThat(eventReceiver.received, is(true));
	}

	@After
	public void tearDown() {
		ABEventBus.getDefault().unregisterAll();
		DatabaseResetter.resetDatabase();
	}

	public static class EventReceiver {
		public boolean received = false;

		public void onEvent(DocumentDownloadEvent event) {
			received = true;
		}
	}

	private class TestData {

		String initials = "KJV";
		Book document;
		Progress progress = JobManager.createJob("INSTALL_BOOK-"+initials, "Installing King James Version", null);
		DocumentDownloadListItem documentDownloadListItem;
		ProgressBar progressBar;

		{
			try {
				document = FakeSwordBookFactory.createFakeRepoBook(initials, "[KJV]\nDescription=My Test Book", "");
			} catch (IOException e) {
				e.printStackTrace();
			} catch (BookException e) {
				e.printStackTrace();
			}
			progress.setTotalWork(100);
			progress.setWork(33);

			Activity activity = Robolectric.buildActivity(Activity.class).create().get();
			documentDownloadListItem = (DocumentDownloadListItem) LayoutInflater.from(activity).inflate(R.layout.document_download_list_item, null);
			documentDownloadListItem.setDocument(document);
			progressBar = documentDownloadListItem.getProgressBar();

		}
	}
}
