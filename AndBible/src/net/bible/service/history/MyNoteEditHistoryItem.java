package net.bible.service.history;

import net.bible.android.control.page.CurrentPageManager;

import org.crosswire.jsword.passage.Key;

import android.content.Intent;

public class MyNoteEditHistoryItem extends IntentHistoryItem {

	private Key verse;
	
	public MyNoteEditHistoryItem(int descriptionId, Intent intent, Key verse) {
		super(descriptionId, intent);
		
		this.verse = verse;
	}

	@Override
	public void revertTo() {
		CurrentPageManager.getInstance().getCurrentMyNotePage().setKey(verse);

		super.revertTo();
	}

	
}
