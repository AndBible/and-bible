package net.bible.android.view.activity.base;

import android.os.Bundle;

public interface AndBibleActivity {
	/** facilitate History List integration */ 
    public void onCreate(Bundle savedInstanceState, boolean integrateWithHistoryManager);
    
    /** allow HistoryManager to know if integration is required */
	public boolean isIntegrateWithHistoryManager();

}
