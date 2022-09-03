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

package net.bible.service.format.osistohtml;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class TextWriterTest {

	private TextWriter textWriter;
	@Before
	public void setUp() throws Exception {
		textWriter = new TextWriter();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testHierarchicalBeginInsertAt() throws Exception {
		textWriter.write("ab");
		int afterAb = textWriter.getPosition();
		textWriter.write("kl");

		textWriter.beginInsertAt(afterAb);
		textWriter.write("cd");
		
		int afterCd = textWriter.getPosition();
		textWriter.write("ef");
		
		// should be ignored because already inserting
		textWriter.beginInsertAt(afterCd);
		textWriter.write("gh");

		textWriter.finishInserting();
		
		textWriter.write("ij");
		textWriter.finishInserting();

		textWriter.write("mn");

		assertThat(textWriter.getHtml(), equalTo("abcdefghijklmn"));
	}

}
