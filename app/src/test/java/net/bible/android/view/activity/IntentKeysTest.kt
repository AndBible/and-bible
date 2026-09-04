/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY,
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity

import net.bible.android.view.activity.ai.PromptEditComposeActivity
import net.bible.android.view.activity.download.DownloadKeys
import net.bible.android.view.activity.ai.RawLlmLogKeys
import net.bible.android.view.activity.progress.ReadingProgressKeys
import net.bible.android.view.activity.readingplan.ReadingPlanKeys
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Batch Z-late phase 1, P2. These keys moved out of classic activity companions. Their VALUES are
 * the contract — a mistyped move produces an intent extra nobody reads, with no compile error and
 * no other failing test.
 */
class IntentKeysTest {
    @Test fun downloadKeysAreUnchanged() {
        assertEquals("documentIds", DownloadKeys.DOCUMENT_IDS_EXTRA)
        assertEquals(1, DownloadKeys.DOWNLOAD_FINISH)
    }

    @Test fun rawLlmLogKeysAreUnchanged() {
        assertEquals("workspace_id", RawLlmLogKeys.EXTRA_WORKSPACE_ID)
        assertEquals("log_record_id", RawLlmLogKeys.EXTRA_LOG_RECORD_ID)
    }

    @Test fun readingProgressKeyIsUnchanged() {
        assertEquals("tab", ReadingProgressKeys.EXTRA_TAB)
    }

    @Test fun readingPlanKeysAreUnchanged() {
        // FQN-shaped values. They are data, not references — do not "modernise" them to match the
        // new file's package, or every reading-plan intent breaks.
        assertEquals("net.bible.android.view.activity.readingplan.Plan", ReadingPlanKeys.PLAN)
        assertEquals("net.bible.android.view.activity.readingplan.Day", ReadingPlanKeys.DAY)
    }

    @Test fun promptIdExtraStaysEqualAcrossBothPromptEditScreens() {
        // PromptEditActivity.EXTRA_PROMPT_ID was borrowed WITHOUT a new *Keys holder: its two
        // consumers were repointed onto PromptEditComposeActivity's own pre-existing duplicate
        // constant instead. So there are two independent "prompt_id" string literals (classic's
        // and the Compose activity's own) that must keep the same value while both screens exist;
        // nothing else pins that.
        assertEquals("prompt_id", PromptEditComposeActivity.EXTRA_PROMPT_ID)
    }
}
