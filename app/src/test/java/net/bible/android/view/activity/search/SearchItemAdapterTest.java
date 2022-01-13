package net.bible.android.view.activity.search;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import org.junit.Before;
import org.junit.Test;

public class SearchItemAdapterTest {
	public static class TestSearch {
		String searchTerms;
		String testType;

		public TestSearch(String searchTerms) {
			this.searchTerms = searchTerms;
			this.testType = testType;
		}

		public String PrepareSearchTerms() {return SearchItemAdapter.prepareSearchTerms(searchTerms);}
		public String[] SplitSearchTerms() {return SearchItemAdapter.splitSearchTerms(searchTerms);}
		public String PrepareSearchWord() {return SearchItemAdapter.prepareSearchWord(searchTerms);}
	}

	@Before
	public void setup() {
	}

	@Test
	public void test_prepareSearchTerms() throws Exception {
		TestSearch test = new TestSearch("strong:g000123");
		String result = test.PrepareSearchTerms();
		assertThat(result, equalTo("strong:g0*123"));
	}
	@Test
	public void test_splitSearchTerms() throws Exception {
		TestSearch test = new TestSearch("moses \"burning bush\"");
		String[] result = test.SplitSearchTerms();
		String[] expectedResult = {"moses","\"burning bush\""};
		assertThat(result, equalTo(expectedResult));
	}
	@Test
	public void test_prepareSearchWord() throws Exception {
		TestSearch test = new TestSearch("+\"burning bush\"");
		String result = test.PrepareSearchWord();
		assertThat(result, equalTo("burning bush"));
	}
}
