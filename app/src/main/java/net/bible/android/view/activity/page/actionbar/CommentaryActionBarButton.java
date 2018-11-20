/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.activity.page.actionbar;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton;

import org.crosswire.jsword.book.Book;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class CommentaryActionBarButton extends QuickDocumentChangeToolbarButton {

	private final DocumentControl documentControl;

	@Inject
	public CommentaryActionBarButton(DocumentControl documentControl) {
		this.documentControl = documentControl;
	}

	@Override
	protected
	Book getSuggestedDocument() {
		return documentControl.getSuggestedCommentary();
	}

	/**
	 * Not important enough to show if limited space
	 * (non-Javadoc)
	 * @see net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton#canShow()
	 */
	@Override
	protected boolean canShow() {
		return super.canShow() &&
				(isWide() || !isSpeakMode());
	}
}
