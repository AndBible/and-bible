package net.bible.android.view.activity.base;

import android.app.Activity;
import android.view.LayoutInflater;
import android.widget.ProgressBar;

import net.bible.android.activity.BuildConfig;
import net.bible.android.activity.R;
import net.bible.android.view.util.widget.TwoLineListItemWithImage;
import net.bible.service.download.FakeSwordBookFactory;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.jsword.book.Book;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class)
public class DocumentDownloadProgressCacheTest {

	private DocumentDownloadProgressCache documentDownloadProgressCache;

	private TestData testData;

	@Before
	public void setUp() throws Exception {
		documentDownloadProgressCache = new DocumentDownloadProgressCache();
		testData = new TestData();
	}

	@Test
	public void updateProgress() throws Exception {

		documentDownloadProgressCache.documentShown(testData.document, testData.progressBar);

		documentDownloadProgressCache.updateProgress(testData.progress);
		assertThat(testData.progressBar.getProgress(), equalTo(33));

		testData.progress.setWork(100);
		documentDownloadProgressCache.updateProgress(testData.progress);
		assertThat(testData.progressBar.getProgress(), equalTo(100));
	}

	@Test
	public void documentHidden() throws Exception {

	}

	@Test
	public void documentShown() throws Exception {

	}

	private class TestData {

		String initials = "KJV";
		Book document;
		Progress progress = JobManager.createJob("INSTALL_BOOK-"+initials, "Installing King James Version", null);
		TwoLineListItemWithImage twoLineListItemWithImage;
		ProgressBar progressBar;

		{
			try {
				document = FakeSwordBookFactory.createFakeRepoBook(initials, "[KJV]\nDescription=My Test Book", "");
				progress.setTotalWork(100);
				progress.setWork(33);

				Activity activity = Robolectric.buildActivity(Activity.class).create().get();
				twoLineListItemWithImage = (TwoLineListItemWithImage) LayoutInflater.from(activity).inflate(R.layout.list_item_2_image, null);
				progressBar = twoLineListItemWithImage.getProgressBar();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}


	}
}