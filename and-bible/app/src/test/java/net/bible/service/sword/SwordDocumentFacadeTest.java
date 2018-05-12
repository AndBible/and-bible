package net.bible.service.sword;

import net.bible.android.TestBibleApplication;
import net.bible.android.activity.BuildConfig;
import net.bible.service.download.FakeSwordBookFactory;

import org.crosswire.jsword.book.Book;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import robolectric.MyRobolectricTestRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

@RunWith(MyRobolectricTestRunner.class)
@Config(constants = BuildConfig.class, application = TestBibleApplication.class)
public class SwordDocumentFacadeTest {

	private SwordDocumentFacade swordDocumentFacade;

	@Before
	public void setUp() throws Exception {
		swordDocumentFacade = new SwordDocumentFacade(null);
	}

	@Test
	public void testIsIndexDownloadAvailable() throws Exception {
		Book fakeBook = FakeSwordBookFactory.createFakeRepoBook("My Book", TestData.ESVS_CONF+"Version=1.0.1", "");
		assertThat(swordDocumentFacade.isIndexDownloadAvailable(fakeBook), equalTo(true));
	}

	interface TestData {
		String ESVS_CONF = "[ESVS]\nDescription=My Test Book\nCategory=BIBLE\nModDrv=zCom\nBlockType=CHAPTER\nLang=en\nEncoding=UTF-8\nLCSH=Bible--Commentaries.\nDataPath=./modules/comments/zcom/mytestbook/\n";
	}
}