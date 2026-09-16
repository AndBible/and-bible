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
package net.bible.android.view.activity.page

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reading-host re-typing R3 (design spec §3.2): the Activity's stubs reach the collaborator, and the
 * collaborator is what holds the state.
 *
 * The three PER-HOST objects the command surface owns — [ReadingCommands.bibleViewFactory], the
 * `textSettingsImagePicker` continuation and `mainMenuCommandHandler` — are instance objects, not
 * Koin singletons. Two instances of any of them is a SILENT bug: a second `BibleViewFactory` would
 * hand out `BibleView`s no window ever registers and leave `clear()` clearing the wrong cache; a
 * second image picker would hold a continuation the launched picker never resumes. Nothing else in
 * the suite would notice, which is why every assertion here is `assertSame` rather than
 * `assertNotNull`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingCommandsDelegationTest {

    @Test
    fun theActivityBuildsItsCommandCollaborator() {
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            val activity = controller.create().get()
            assertNotNull("MainBibleActivity must own a ReadingCommands", activity.readingCommands)
            assertSame(
                "the factory is the collaborator's; the Activity's accessor must be a view onto it, " +
                    "not a second instance",
                activity.readingCommands.bibleViewFactory,
                activity.bibleViewFactory,
            )
            assertSame(
                "the text-settings image picker is the collaborator's too — a second continuation " +
                    "holder would never be resumed by the launched picker",
                activity.readingCommands.textSettingsImagePicker,
                activity.textSettingsImagePicker,
            )
        } finally {
            controller.close()
        }
    }

    /**
     * The collaborator is built ONCE per Activity, not per read: `readingCommands` must be a stored
     * property, not a `get() = ReadingCommands(this)` that mints a new one — which would satisfy
     * every `assertSame` above (each call would still return one consistent object graph) while
     * quietly re-registering an activity-result launcher on every access.
     */
    @Test
    fun theCollaboratorIsTheSameObjectOnEveryRead() {
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            val activity = controller.create().get()
            assertSame(
                "readingCommands must be one stored instance, not a fresh one per read",
                activity.readingCommands,
                activity.readingCommands,
            )
        } finally {
            controller.close()
        }
    }
}
