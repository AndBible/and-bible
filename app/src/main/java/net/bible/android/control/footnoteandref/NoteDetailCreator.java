package net.bible.android.control.footnoteandref;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Logger;
import net.bible.service.format.Note;
import net.bible.service.sword.SwordContentFacade;

import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@ApplicationScope
public class NoteDetailCreator {

	private final SwordContentFacade swordContentFacade;

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private final Logger log = new Logger(this.getClass().getName());

	@Inject
	public NoteDetailCreator(SwordContentFacade swordContentFacade, ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.swordContentFacade = swordContentFacade;
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}

	public String getDetail(Note note) {
		String retval = "";
		try {
			if (Note.NoteType.TYPE_REFERENCE.equals(note.getNoteType())) {
				String verse = StringUtils.isNotEmpty(note.getOsisRef()) ? note.getOsisRef() : note.getNoteText();

				retval = swordContentFacade.getPlainText(activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentBible().getCurrentDocument(), verse);
				retval = CommonUtils.limitTextLength(retval);
			}
		} catch (Exception e) {
			log.error("Error getting note detail for osisRef "+note.getOsisRef(), e);
		}
		return retval;
	}
}
