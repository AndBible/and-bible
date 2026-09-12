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

import net.bible.android.view.activity.download.DownloadKeys
import net.bible.android.view.activity.ai.RawLlmLogKeys
import net.bible.android.view.activity.progress.ReadingProgressKeys
import net.bible.android.view.activity.readingplan.ReadingPlanKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Batch Z-late phase 1, P2. These keys moved out of classic activity companions. Their VALUES are
 * the contract — a mistyped move produces an intent extra nobody reads, with no compile error and
 * no other failing test.
 */
class IntentKeysTest {
    @Test fun downloadKeysAreUnchanged() {
        assertEquals("documentIds", DownloadKeys.DOCUMENT_IDS_EXTRA)
    }

    /**
     * nav-graph slice 4 Task 7b, plan D7: `DownloadKeys.DOWNLOAD_FINISH` is a dead result code — all
     * ten launch edges into Download discarded the result code, and both post-hooks
     * (`StartupActivity.afterDownload`, `StartupComposeActivity.afterFlow`) re-derive whether to
     * proceed from `SwordDocumentFacade.bibles` instead of reading it. Reflection rather than a
     * direct `DownloadKeys.DOWNLOAD_FINISH` reference, so this test keeps compiling after the
     * constant is deleted instead of failing to build the moment it lands.
     */
    @Test fun downloadFinishIsGone() {
        assertFalse(
            "DownloadKeys.DOWNLOAD_FINISH is a dead result code (plan D7); every consumer already " +
                "ignores it, and both post-hooks re-derive from SwordDocumentFacade.bibles instead",
            DownloadKeys::class.java.declaredFields.any { it.name == "DOWNLOAD_FINISH" },
        )
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

    // promptIdExtraStaysEqualAcrossBothPromptEditScreens was DELETED here (nav-graph Task 10),
    // not converted, and deliberately NOT kept the way rawLlmLogKeysAreUnchanged/RawLlmLogKeys was
    // at Task 8. That test pinned two INDEPENDENT "prompt_id" Intent-extra literals (classic's
    // PromptEditActivity and PromptEditComposeActivity's own pre-existing duplicate constant)
    // agreeing while both screens existed; Task 10 deletes the second and last of those screens,
    // so there is no longer a second literal for this test to keep in sync with. Unlike
    // RawLlmLogKeys, EXTRA_PROMPT_ID was never lifted into its own standalone *Keys holder, was
    // read nowhere else in the tree (verified: `grep -rn '"prompt_id"' app/src sharedCore/src
    // sharedUi/src` had exactly one non-test hit, the deleted class's own constant), and the
    // nav-graph's own prompt-id argument is `NavRoutes.ARG_PROMPT_ID = "promptId"` -- a DIFFERENT
    // string serving a route-argument name, not an Intent-extra key, so "moving" this pinned value
    // onto it would not preserve what the test asserted; it would compare a literal to itself.
    // RawLlmLogKeys survives Task 10 as deliberate wire-format documentation (see its KDoc); this
    // constant leaves no such trail worth keeping.
}
