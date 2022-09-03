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

package net.bible.service.readingplan;

import net.bible.android.control.versification.TestData;

import org.crosswire.jsword.passage.Key;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class PassageReaderTest {

	private PassageReader passageReader;

	@Before
	public void setup() {
		passageReader = new PassageReader(TestData.KJV);
	}

	/**
	 * various names were use for Song of Songs - check which is correct.
	 */
	@Test
	public void testSongOfSongsChapter() {
		final Key key = passageReader.getKey("Song.8");
		assertThat(key.getCardinality(), greaterThan(10));
	}

	@Test
	public void testSongOfSongsChapters() {
		final Key key = passageReader.getKey("Song.1-Song.3");
		assertThat(key.getCardinality(), greaterThan(30));
	}

	@Test
	public void testSongOfSongsBook() {
		final Key key = passageReader.getKey("Song");
		assertThat(key.getCardinality(), greaterThan(100));
	}
}
