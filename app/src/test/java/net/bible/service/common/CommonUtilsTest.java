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

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Test;


import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class CommonUtilsTest {

	@Test
	public void testGetVerseDescription() {
		Versification kjv = Versifications.instance().getVersification("KJV");
		Verse gen1_0 = new Verse(kjv, BibleBook.GEN, 1, 0);
		assertThat(CommonUtils.INSTANCE.getKeyDescription(gen1_0), equalTo("Genesis 1"));

		Verse gen1_1 = new Verse(kjv, BibleBook.GEN, 1, 1);
		assertThat(CommonUtils.INSTANCE.getKeyDescription(gen1_1), equalTo("Genesis 1:1"));

		Verse gen1_10 = new Verse(kjv, BibleBook.GEN, 1, 10);
		assertThat(CommonUtils.INSTANCE.getKeyDescription(gen1_10), equalTo("Genesis 1:10"));
	}

}
