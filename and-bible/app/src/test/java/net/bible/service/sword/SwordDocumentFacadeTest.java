package net.bible.service.sword;

import net.bible.android.activity.BuildConfig;
import net.bible.service.download.FakeSwordBookFactory;

import org.crosswire.jsword.book.Book;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class)
public class SwordDocumentFacadeTest {

	private SwordDocumentFacade swordDocumentFacade;


	@Before
	public void setUp() throws Exception {
		swordDocumentFacade = SwordDocumentFacade.getInstance();
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