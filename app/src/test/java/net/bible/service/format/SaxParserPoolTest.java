package net.bible.service.format;

import org.junit.Before;
import org.junit.Test;

import javax.xml.parsers.SAXParser;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.*;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class SaxParserPoolTest {

	private SaxParserPool saxParserPool;

	@Before
	public void setup() {
		saxParserPool = new SaxParserPool();
	}

	@Test
	public void obtain() throws Exception {
		SAXParser firstParser = saxParserPool.obtain();
		assertThat(firstParser, not(nullValue()));
	}

	@Test
	public void recycle() throws Exception {
		SAXParser firstParser = saxParserPool.obtain();
		saxParserPool.recycle(firstParser);
		SAXParser secondParser = saxParserPool.obtain();
		assertThat(secondParser, equalTo(firstParser));
	}

	@Test
	public void addMoreInstances() throws Exception {
		SAXParser firstParser = saxParserPool.obtain();
		SAXParser secondParser = saxParserPool.obtain();
		assertThat(secondParser, not(equalTo(firstParser)));
	}
}