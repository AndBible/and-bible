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

package net.bible.android.view.compose

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Reading-host re-typing R6c1: `ReadingCommands` and `MenuCommandHandler` do not ask their Activity
 * for values that are reachable without one — the same move [ReadingHostDelegationGuardTest] pins
 * for `ComposeReadingViewHost` (R1), applied to the two collaborators R6c splits off from R6.
 *
 * Fix round 1 (review Important 1 + Important 2) widened this from the original cut, which wrongly
 * treated `MenuCommandHandler`'s `packageName`/`resources` as untouchable ("already on the
 * interface") when they are plain `ContextWrapper` members needing only *a* Context, and wrongly
 * treated `windowRepository` as a free `windowControl.windowRepository` substitution when the two
 * can legitimately hold DIFFERENT objects for a second, not-yet-resumed `MainBibleActivity`
 * (`MainBibleActivity.onResume`/`unFreeze()` only exist to reconcile them).
 *
 * R6c2 update: both collaborators are now re-typed (R4's `ReadingHostActivity` plus a
 * `ReadingCommandsHostCallbacks` bundle), so the property names these scans keyed on --
 * `activity` and `mainBibleActivity` -- are gone from both files. Each scan below was rewritten to
 * keep watching the SAME defect through the new spelling rather than passing vacuously against a
 * receiver that no longer exists; [CollaboratorTypeGuardTest] is what pins the re-typing itself.
 *
 * Deliberately does NOT cover the rest of the "Already on the interface / LifecycleOwner" row
 * (`getString`/`lifecycleScope`/`startActivity`): those stay spelled `activity.foo()` /
 * `mainBibleActivity.foo()` on purpose (R1's own precedent for the same bucket on
 * `ComposeReadingViewHost`) because `ReadingHostActivity` already carries `getString` and
 * `lifecycleScope`, so R6c2's re-typing needs no further change there for the SINGLE-arg `getString`
 * calls and `lifecycleScope`. (`startActivity` and the four vararg `getString(resId, args…)` calls
 * do NOT survive R6c2 unchanged either, per the review — but fixing those is deferred, not this
 * guard's job; see the task report's fix-round section for the full list.)
 *
 * **Anti-vacuity.** The positive assertions are load-bearing: without them this file would pass
 * against an empty or renamed source, which is the failure mode spec §5 exists to prevent.
 */
class ReadingCommandsHostDelegationGuardTest {
    private val readingCommandsFile =
        File("src/main/java/net/bible/android/view/activity/page/ReadingCommands.kt")
    private val menuCommandHandlerFile =
        File("src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt")

    /** Non-prose lines only, same idiom as [SpeakEntryPointGuardTest.codeLinesOf]: this file's own
     *  kdoc quotes the very strings these scans look for (e.g. this test's production counterpart
     *  documents itself in prose using `windowControl.windowRepository` as the name of what it is
     *  NOT), so a raw whole-file `contains`/regex scan would flag its own documentation. */
    private fun codeLinesOf(file: File): String =
        file.readLines().filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("import ") || trimmed.startsWith("//") ||
                trimmed.startsWith("*") || trimmed.startsWith("/*")
        }.joinToString("\n")

    private val readingCommandsSource: String get() = codeLinesOf(readingCommandsFile)
    private val menuCommandHandlerSource: String get() = codeLinesOf(menuCommandHandlerFile)

    @Test
    fun theScannedSourcesAreActuallyThere() {
        assertTrue(readingCommandsFile.exists(), "ReadingCommands.kt not found — has it moved?")
        assertTrue(
            readingCommandsSource.contains("class ReadingCommands("),
            "the scanned file must be the class itself, or this guard proves nothing",
        )
        assertTrue(menuCommandHandlerFile.exists(), "MenuCommandHandler.kt not found — has it moved?")
        assertTrue(
            menuCommandHandlerSource.contains("class MenuCommandHandler("),
            "the scanned file must be the class itself, or this guard proves nothing",
        )
    }

    /** The Koin singletons and the global settings read ([toolbarButtonSetting]) —
     *  `windowRepository` is checked separately below: ONE reference to `activity.windowRepository`
     *  is legitimate (the owning-host supplier's own binding), so it cannot be a flat zero-count. */
    private val readingCommandsDelegated = listOf(
        "windowControl",
        "documentControl",
        "speakControl",
        "pageControl",
        "bookmarkControl",
        "searchControl",
        "navigationControl",
        "toolbarButtonSetting",
    )

    /**
     * `MenuCommandHandler`'s own free half (R6c1 fix round 1, review Important 1): plain
     * `ContextWrapper` members that need only a Context, not a specific Activity.
     *
     * R6c2 re-keyed this on the CURRENT receiver, `hostActivity`. Keying it on the old
     * `mainBibleActivity` spelling would have made it a check that cannot fail: that receiver does
     * not exist in the file any more, so every count would be zero forever, whatever the code did.
     */
    private val menuCommandHandlerDelegated = listOf(
        "packageName",
        "resources",
    )

    @Test
    fun neitherCollaboratorAsksTheActivityForTheFreeHalf() {
        val offenders = readingCommandsDelegated.flatMap { member ->
            // R6c2: `activity` became `readingHost` (the interface) / `hostActivity` (the plain
            // Activity). Both spellings are offenders for these members, and so is the old one --
            // which would mean the re-typing had been undone.
            Regex("""(?<![.\w])(?:activity|readingHost|hostActivity)\.$member\b""")
                .findAll(readingCommandsSource).map { "ReadingCommands.kt: ${it.value}" }
        } + menuCommandHandlerDelegated.flatMap { member ->
            Regex("""(?<![.\w])(?:mainBibleActivity|hostActivity)\.$member\b""")
                .findAll(menuCommandHandlerSource).map { "MenuCommandHandler.kt: ${it.value}" }
        }
        assertEquals(
            emptyList(), offenders.distinct().sorted(),
            "these are reachable without the Activity — inject the Koin singletons, read " +
                "toolbarButtonSetting from CommonUtils.settings directly, or route packageName/" +
                "resources through BibleApplication.application (R6c1)",
        )
    }

    /**
     * Anti-vacuity for the scan above: it can only catch anything while `MenuCommandHandler`
     * really does hold an Activity-shaped receiver under the name the regex looks for, and while
     * the two Context reads it replaced are still made the host-free way.
     */
    @Test
    fun menuCommandHandlerStillHoldsAnActivityReceiverAndReadsItsContextFreeOfIt() {
        assertTrue(
            menuCommandHandlerSource.contains("private val hostActivity: ActivityBase"),
            "the receiver the scan above is keyed on is gone or renamed — rekey the scan, do not " +
                "let it pass against a name that no longer exists",
        )
        assertTrue(
            menuCommandHandlerSource.contains("BibleApplication.application.packageName"),
            "packageName must still come from the application Context (R6c1 fix round 1)",
        )
        assertTrue(
            menuCommandHandlerSource.contains("BibleApplication.application.resources"),
            "resources must still come from the application Context (R6c1 fix round 1)",
        )
    }

    @Test
    fun readingCommandsHasItsOwnInjectedFieldsForEveryKoinSingleton() {
        val koinSingletons = listOf(
            "windowControl" to "WindowControl",
            "documentControl" to "DocumentControl",
            "speakControl" to "SpeakControl",
            "pageControl" to "PageControl",
            "bookmarkControl" to "BookmarkControl",
            "searchControl" to "SearchControl",
            "navigationControl" to "NavigationControl",
        )
        koinSingletons.forEach { (member, type) ->
            assertTrue(
                readingCommandsSource.contains("val $member: $type by inject()"),
                "the replacement for activity.$member is ReadingCommands' own injected field " +
                    "(`val $member: $type by inject()`); if it is gone, the deletions were done by " +
                    "re-introducing a different coupling",
            )
        }
    }

    /**
     * Fix round 1, review Important 2. `windowRepository` is NOT a free `windowControl` substitution:
     * `windowControl.windowRepository` is whichever host most recently resumed, which can differ from
     * THIS host's own repository for a second, not-yet-resumed `MainBibleActivity`. The correct shape
     * is a supplier bound to the owning host, read at call time — this test pins that shape directly
     * rather than re-deriving it from a reference count, so a future refactor that keeps the supplier
     * but renames it cannot silently pass.
     */
    @Test
    fun windowRepositoryIsAnOwningHostSupplierNotWindowControls() {
        assertFalse(
            readingCommandsSource.contains("windowControl.windowRepository"),
            "windowControl.windowRepository is whichever host most recently resumed, not " +
                "necessarily THIS host's own repository — R6c1's original (wrong) substitution",
        )
        // R6c2 moved the BINDING out to the host bundle (so a second host binds its own) and left
        // the supplier itself here. Both halves are pinned: a `get()` and not a `=`, because a
        // stored `val windowRepository = hostCallbacks.windowRepository()` would be exactly the
        // captured-once bug this supplier exists to prevent.
        assertTrue(
            readingCommandsSource.contains(
                "private val windowRepository: () -> WindowRepository get() = hostCallbacks.windowRepository"
            ),
            "the owning-host windowRepository supplier is gone or renamed",
        )
        assertTrue(
            File("src/main/java/net/bible/android/view/activity/page/ReadingCommandsHostCallbacks.kt")
                .readText().contains("val windowRepository: () -> WindowRepository,"),
            "the bundle must declare the owning host's repository as a SUPPLIER, not a value",
        )
        assertTrue(
            File("src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt")
                .readText().contains("windowRepository = { windowRepository },"),
            "MainBibleActivity must bind its OWN windowRepository into the bundle",
        )
        val supplierCallSites = Regex("""(?<![.\w])windowRepository\(\)""").findAll(readingCommandsSource).count()
        assertEquals(
            25, supplierCallSites,
            "expected every call site (R6c1's 15, plus the ten R6c2 added when it moved " +
                "dummyStrongsPrefOption, showLlmPromptSelector, cycleWorkspace and " +
                "currentWorkspaceId here) to route through the windowRepository() supplier",
        )
    }
}
