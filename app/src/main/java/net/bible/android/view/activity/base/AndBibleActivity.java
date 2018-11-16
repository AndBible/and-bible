package net.bible.android.view.activity.base;

import android.content.Intent;
import android.os.Bundle;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
public interface AndBibleActivity {
	/** facilitate History List integration */ 
	public void onCreate(Bundle savedInstanceState, boolean integrateWithHistoryManager);
	
	/** allow HistoryManager to know if integration is required */
	public boolean isIntegrateWithHistoryManager();
	
	/** allow HistoryManager to know if integration is required */
	public void setIntegrateWithHistoryManager(boolean integrateWithHistoryManager);

	/** allow activity to enhance intent to correctly restore state */
	public Intent getIntentForHistoryList();
}
