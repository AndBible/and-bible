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
package net.bible.android

import net.bible.sharedcore.ui.dialog.AppDialogController
import org.junit.rules.ExternalResource
import org.koin.java.KoinJavaComponent

/**
 * Resets the app-wide [AppDialogController] before AND after every `@Test`.
 *
 * [AppDialogController] is a Koin `single` (`CoreModule.kt`), and `BibleApplication.onCreate`
 * only calls `startKoin` when `GlobalContext.getOrNull() == null` -- so under Robolectric it
 * starts exactly ONCE for the whole unit-test JVM (see `TestBibleApplication`'s KDoc: "onCreate
 * runs many times in the same [process]"). Every Robolectric test class in that JVM therefore
 * shares the SAME controller instance and the SAME queue.
 *
 * A request left pending at the end of one test class -- its own unanswered dialog after an
 * assertion failure, or a fire-and-forget raiser it never itself exercises (e.g.
 * `ReadingAppBootstrap.showFirstRunNotices()`, launched un-awaited from
 * `NavHostComposeActivity.bootstrapIfNeeded()` behind a process-wide "only the first host ever"
 * gate) -- becomes the NEXT class's stale queue head: `pending.value` returns someone else's
 * request, `respond()` for the id the new test expects is a silent no-op, and the caller that
 * really is waiting on its own answer hangs until its coroutine's timeout.
 *
 * `cancelAll()` in `before()` guarantees a clean queue no matter what any earlier class in the
 * SAME run left behind, independent of whether that class remembered its own teardown.
 * `cancelAll()` in `after()` stops this class handing the problem on, on top of (not instead of)
 * any cleanup the class's own `@After` already does -- `cancelAll()` on an empty queue is a no-op,
 * so the two never conflict.
 *
 * A [org.junit.rules.TestRule] wraps the whole test `Statement`, including the class's own
 * `@Before`/`@After` methods, so [before] runs before them and [after] runs after them.
 *
 * Apply to any Robolectric test class that reads or writes [AppDialogController] through Koin
 * (`KoinJavaComponent.get(AppDialogController::class.java)`) -- not to a test that renders
 * `AppDialogHost` directly off a hand-built `ShownDialog` with no Koin `Application`
 * (`AppDialogHostTest`), which never touches the shared singleton at all.
 *
 * ```
 * @get:Rule val dialogReset = AppDialogControllerResetRule()
 * ```
 */
class AppDialogControllerResetRule : ExternalResource() {
    private val controller: AppDialogController
        get() = KoinJavaComponent.get(AppDialogController::class.java)

    override fun before() {
        controller.cancelAll()
    }

    override fun after() {
        controller.cancelAll()
    }
}
