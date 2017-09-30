package net.bible.service.common;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowStatFs;

import java.io.File;

import robolectric.MyRobolectricTestRunner;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@RunWith(MyRobolectricTestRunner.class)
public class FileManagerTest {
	private final String folder = "src/test/resources/net/bible/service/common".replace("/", File.separator);

	@Test
	public void shouldCopyFile() throws Exception {
		// ensure Android thinks there is enough room
		ShadowStatFs.registerStats(folder, 100, 20, 10);

		File toCopy = new File(folder, "testFileToCopy");
		File target = new File(folder, "copiedFile");
		target.deleteOnExit();

		assertTrue("copy failed", FileManager.copyFile(toCopy, target));

		assertTrue(target.exists());
	}

	@Test
	public void shouldOverwriteOnCopyIfTargetFileExists() throws Exception {
		// ensure Android thinks there is enough room
		ShadowStatFs.registerStats(folder, 100, 20, 10);

		File toCopy = new File(folder, "testFileToCopy");
		File target = new File(folder, "copiedFile");
		target.deleteOnExit();

		assertTrue("initial copy failed", FileManager.copyFile(toCopy, target));
		assertTrue("overwriting copy failed", FileManager.copyFile(toCopy, target));
		assertEquals("copied file has different length", toCopy.length(),  target.length());

		assertTrue(target.exists());
	}
}