package net.bible.android.control;

import net.bible.android.control.document.DocumentControl;

public class ControlFactory {

	private DocumentControl documentControl = new DocumentControl();
	
	private static ControlFactory singleton = new ControlFactory();
	
	public static ControlFactory getInstance() {
		return singleton;
	}
	
	public DocumentControl getDocumentControl() {
		return documentControl;		
	}
}
