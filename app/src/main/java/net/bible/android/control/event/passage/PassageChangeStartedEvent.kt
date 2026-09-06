/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.event.passage

/**
 * POSTED WITH NO SUBSCRIBERS since Batch Z-late phase 1 removed the classic reading view.
 *
 * Recorded rather than swept, deliberately and consistently across the whole batch: deleting an
 * unreachable HANDLER is a local tidy-up, while deleting a posted EVENT changes what the app
 * announces about itself, and any later subscriber -- Compose, iOS or a future feature -- would
 * want it back. Whoever revisits this should decide the poster's fate first, not the class's.
 */
class PassageChangeStartedEvent 
