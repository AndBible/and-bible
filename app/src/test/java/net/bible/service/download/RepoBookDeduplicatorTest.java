package net.bible.service.download;

import org.crosswire.jsword.book.Book;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

public class RepoBookDeduplicatorTest {

	private RepoBookDeduplicator repoBookDeduplicator;
	
	@Before
	public void setUp() throws Exception {
		repoBookDeduplicator = new RepoBookDeduplicator();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAdd1() throws Exception {
		Book svBook = FakeSwordBookFactory.createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.0", null);
		List<Book> books = Arrays.asList(svBook);
		
		repoBookDeduplicator.addAll(books);
		
		assertThat(repoBookDeduplicator.getBooks(), contains(svBook));
	}

	@Test
	public void testAddNewer() throws Exception {
		Book svBook = FakeSwordBookFactory.createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.0", null);
		List<Book> books1 = Arrays.asList(svBook);
		repoBookDeduplicator.addAll(books1);

		Book svBookNewer = FakeSwordBookFactory.createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.1", null);
		List<Book> books2 = Arrays.asList(svBookNewer);
		
		repoBookDeduplicator.addAll(books2);
		
		assertThat(repoBookDeduplicator.getBooks(), contains(svBookNewer));
		assertThat(repoBookDeduplicator.getBooks().get(0).getProperty("Version"), equalTo("1.0.1"));
	}

	@Test
	public void testAddOlder() throws Exception {
		Book svBook = FakeSwordBookFactory.createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.1", null);
		List<Book> books1 = Arrays.asList(svBook);
		repoBookDeduplicator.addAll(books1);

		Book svBookOlder = FakeSwordBookFactory.createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.0", null);
		List<Book> books2 = Arrays.asList(svBookOlder);
		
		repoBookDeduplicator.addAll(books2);
		
		assertThat(repoBookDeduplicator.getBooks(), contains(svBook));
		assertThat(repoBookDeduplicator.getBooks().get(0).getProperty("Version"), equalTo("1.0.1"));
	}

	@Test
	public void testAddIfNotExists() throws Exception {
		Book svBook = FakeSwordBookFactory.createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.0", null);
		List<Book> books1 = Arrays.asList(svBook);
		repoBookDeduplicator.addAll(books1);
		
		repoBookDeduplicator.addIfNotExists(books1);
		
		assertThat(repoBookDeduplicator.getBooks(), contains(svBook));
		assertThat(repoBookDeduplicator.getBooks().get(0).getProperty("Version"), equalTo("1.0.0"));
	}

	@Test
	public void testAddIfNotExistsNewer() throws Exception {
		Book svBook = FakeSwordBookFactory.createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.0", null);
		List<Book> books1 = Arrays.asList(svBook);
		repoBookDeduplicator.addAll(books1);

		Book svBookNewer = FakeSwordBookFactory.createFakeRepoBook("DEF", "[DEF]\nLang=sv\nCategory=Biblical Texts\nVersion=1.0.1", null);
		List<Book> books2 = Arrays.asList(svBookNewer);
		
		repoBookDeduplicator.addIfNotExists(books2);
		
		assertThat(repoBookDeduplicator.getBooks(), contains(svBook));
		assertThat(repoBookDeduplicator.getBooks().get(0).getProperty("Version"), equalTo("1.0.0"));
	}

}
