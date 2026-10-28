/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.sword.index

import org.crosswire.jsword.index.IndexStatus

/**
 * Whether an index build for the document is queued or running (SCHEDULED or CREATING). While it
 * is, the document's index is not usable: an EPUB's FTS table already exists before every row is
 * written, and a SWORD module's status flips to DONE only when its build ends. Null (no document)
 * is not in progress.
 */
val IndexStatus?.isInProgress: Boolean
    get() = this == IndexStatus.SCHEDULED || this == IndexStatus.CREATING
