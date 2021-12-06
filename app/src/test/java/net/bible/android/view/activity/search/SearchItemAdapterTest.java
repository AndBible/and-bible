package net.bible.android.view.activity.search;

import net.bible.android.view.activity.search.SearchItemAdapter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class SearchItemAdapterTest {

	@Before
	public void setup() {
	}

	@Test
	public void test_prepareSearchTerms() throws Exception {
		SearchItemAdapter.testSearch test = new SearchItemAdapter.testSearch("strong:g000123");
		String result = test.PrepareSearchTerms();
		assertThat(result, equalTo("strong:g0*123"));
	}
	@Test
	public void test_splitSearchTerms() throws Exception {
		SearchItemAdapter.testSearch test = new SearchItemAdapter.testSearch("moses \"burning bush\"");
		String[] result = test.SplitSearchTerms();
		String[] expectedResult = {"moses","\"burning bush\""};
		assertThat(result, equalTo(expectedResult));
	}
	@Test
	public void test_prepareSearchWord() throws Exception {
		SearchItemAdapter.testSearch test = new SearchItemAdapter.testSearch("+\"burning bush\"");
		String result = test.PrepareSearchWord();
		assertThat(result, equalTo("burning bush"));
	}
}
