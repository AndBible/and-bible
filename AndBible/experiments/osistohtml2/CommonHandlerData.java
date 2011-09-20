package net.bible.service.format.osistohtml;

/** common data required by most TagHandlers
 * 
 * @author denha1m
 *
 */
public class CommonHandlerData {
	// all handlers send output here
	private HtmlTextWriter writer;
	
	// various flags reflecting user Settings
	private OsisToHtmlParameters parameters;

	
	public CommonHandlerData(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}
	
	public HtmlTextWriter getWriter() {
		return writer;
	}

	public OsisToHtmlParameters getParameters() {
		return parameters;
	}
}
