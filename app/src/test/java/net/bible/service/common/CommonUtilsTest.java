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
