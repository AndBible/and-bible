package net.bible.service.css;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import robolectric.MyRobolectricTestRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(MyRobolectricTestRunner.class)
public class CssControlTest {

	private CssControl cssControl;
	
	@Before
	public void setUp() throws Exception {
		cssControl = new CssControl();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetAllStylesheetLinks() throws Exception {
		assertThat(cssControl.getAllStylesheetLinks().get(0), equalTo("<link href='file:///android_asset/web/style.css' rel='stylesheet' type='text/css'/>"));
	}

}
