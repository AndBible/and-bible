/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.common;

import net.bible.test.DatabaseResetter;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowStatFs;

import java.io.File;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={28})
public class FileManagerTest {
	private final String folder = "src/test/resources/net/bible/service/common".replace("/", File.separator);

	@After
	public void tearDown(){
		DatabaseResetter.resetDatabase();
	}

	@Test
	public void shouldCopyFile() throws Exception {
		// ensure Android thinks there is enough room
		ShadowStatFs.registerStats(folder, 100, 20, 10);

		File toCopy = new File(folder, "testFileToCopy");
		File target = new File(folder, "copiedFile");
		target.deleteOnExit();

		assertTrue("copy failed", FileManager.INSTANCE.copyFile(toCopy, target));

		assertTrue(target.exists());
	}

	@Test
	public void shouldOverwriteOnCopyIfTargetFileExists() throws Exception {
		// ensure Android thinks there is enough room
		ShadowStatFs.registerStats(folder, 100, 20, 10);

		File toCopy = new File(folder, "testFileToCopy");
		File target = new File(folder, "copiedFile");
		target.deleteOnExit();

		assertTrue("initial copy failed", FileManager.INSTANCE.copyFile(toCopy, target));
		assertTrue("overwriting copy failed", FileManager.INSTANCE.copyFile(toCopy, target));
		assertEquals("copied file has different length", toCopy.length(),  target.length());

		assertTrue(target.exists());
	}
}
