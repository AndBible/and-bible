package net.bible.service.sword.index;

import org.crosswire.jsword.index.IndexPolicyAdapter;

public class AndroidIndexPolicy extends IndexPolicyAdapter {

	@Override
	public int getRAMBufferSize() {
		return 1;
	}

	@Override
	public boolean isSerial() {
		return true;
	}
}
