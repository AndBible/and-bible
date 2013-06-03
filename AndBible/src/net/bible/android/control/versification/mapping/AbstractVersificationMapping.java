package net.bible.android.control.versification.mapping;

import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

public abstract class AbstractVersificationMapping implements VersificationMapping {

	private Versification leftVersification;
	private Versification rightVersification;

	public AbstractVersificationMapping(String leftVersificationName, String rightVersificationName) {
		this(Versifications.instance().getVersification(leftVersificationName), Versifications.instance().getVersification(rightVersificationName));
	}

	public AbstractVersificationMapping(Versification leftVersification, Versification rightVersification) {
		this.leftVersification = leftVersification;
		this.rightVersification = rightVersification;
	}

	@Override
	public boolean canConvert(Versification from, Versification to) {
		return (from.equals(leftVersification) && to.equals(rightVersification)) ||
			   (from.equals(rightVersification) && to.equals(leftVersification));
	}

	@Override
	public String toString() {
		return leftVersification.getName() + rightVersification.getName() + "Mapping";
	}

	public Versification getLeftVersification() {
		return leftVersification;
	}

	public Versification getRightVersification() {
		return rightVersification;
	}
}