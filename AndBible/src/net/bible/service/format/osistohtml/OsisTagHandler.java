package net.bible.service.format.osistohtml;

import org.xml.sax.Attributes;

public interface OsisTagHandler {

	public abstract String getTagName();

	public abstract void start(Attributes attrs);

	public abstract void end();

}