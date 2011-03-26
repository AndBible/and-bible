package net.bible.android.control.speak;

import net.bible.android.BibleApplication;

public class NumPagesToSpeakDefinition {
	private int numPages;
	private int resourceId;
	private boolean isPlural;
	private int radioButtonId;
	
	public NumPagesToSpeakDefinition(int numPages, int resourceId, boolean isPlural, int radioButtonId) {
		super();
		this.numPages = numPages;
		this.resourceId = resourceId;
		this.isPlural = isPlural;
		this.radioButtonId = radioButtonId;
	}
	
	public String getPrompt() {
		String prompt = null;
		if (isPlural) {
			prompt = BibleApplication.getApplication().getResources().getQuantityString(resourceId, numPages, numPages);
		} else {
			prompt = BibleApplication.getApplication().getResources().getString(resourceId);
		}
		return prompt;
	}
	
	public int getRadioButtonId() {
		return radioButtonId;
	}

	public void setNumPages(int numPages) {
		this.numPages = numPages;
	}

	public int getNumPages() {
		return numPages;
	}
}
