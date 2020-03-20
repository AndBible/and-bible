/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

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
				retval = CommonUtils.INSTANCE.limitTextLength(retval);
			}
		} catch (Exception e) {
			log.error("Error getting note detail for osisRef "+note.getOsisRef(), e);
		}
		return retval;
	}
}
