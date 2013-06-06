package net.bible.android.control.versification.mapping;

import static net.bible.android.control.versification.mapping.VersificationConstants.GERMAN_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.KJV_V11N;
import static net.bible.android.control.versification.mapping.VersificationConstants.NRSV_V11N;
import net.bible.android.control.versification.mapping.base.TwoStepVersificationMapping;

public class NRSVGermanVersificationMapping extends TwoStepVersificationMapping {

	public NRSVGermanVersificationMapping() {
		super(NRSV_V11N, KJV_V11N, GERMAN_V11N);
	}

}
