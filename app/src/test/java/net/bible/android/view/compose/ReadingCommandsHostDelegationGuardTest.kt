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
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Reading-host re-typing R6c1: `ReadingCommands` and `MenuCommandHandler` do not ask their Activity
 * for values that are reachable without one — the same move [ReadingHostDelegationGuardTest] pins
 * for `ComposeReadingViewHost` (R1), applied to the two collaborators R6c splits off from R6.
 *
 * Eight members (design spec addendum 2026-09-17, R6c "Koin singletons" + "Derivable from the
 * above" + "A global object" rows) are Koin singletons, a value derivable from one of them, or a
 * global settings read — every one of them survives the Activity's eventual deletion untouched.
 * Deliberately does NOT cover the "Already on the interface / LifecycleOwner" row
 * (`getString`/`lifecycleScope`/`startActivity`/`packageName`/`resources`): those stay spelled
 * `activity.foo()` / `mainBibleActivity.foo()` on purpose (R1's own precedent for the same bucket
 * on `ComposeReadingViewHost`) because `ReadingHostActivity` already carries them, so R6c2's
 * re-typing needs no further change there.
 *
 * **Anti-vacuity.** The positive assertions are load-bearing: without them this file would pass
 * against an empty or renamed source, which is the failure mode spec §5 exists to prevent.
 */
class ReadingCommandsHostDelegationGuardTest {
    private val readingCommandsFile =
        File("src/main/java/net/bible/android/view/activity/page/ReadingCommands.kt")
    private val menuCommandHandlerFile =
        File("src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt")

    private val readingCommandsSource: String get() = readingCommandsFile.readText()
    private val menuCommandHandlerSource: String get() = menuCommandHandlerFile.readText()

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

    /** The Koin singletons, plus the value derivable from one of them ([windowRepository]) and the
     *  global settings read ([toolbarButtonSetting]) — R6c's "free half" (addendum 2026-09-17). */
    private val delegated = listOf(
        "windowControl",
        "documentControl",
        "speakControl",
        "pageControl",
        "bookmarkControl",
        "searchControl",
        "navigationControl",
        "windowRepository",
        "toolbarButtonSetting",
    )

    @Test
    fun neitherCollaboratorAsksTheActivityForTheFreeHalf() {
        val offenders = delegated.flatMap { member ->
            Regex("""(?<![.\w])activity\.$member\b""").findAll(readingCommandsSource).map {
                "ReadingCommands.kt: activity.$member"
            }
        } + delegated.flatMap { member ->
            Regex("""(?<![.\w])mainBibleActivity\.$member\b""").findAll(menuCommandHandlerSource).map {
                "MenuCommandHandler.kt: mainBibleActivity.$member"
            }
        }
        assertEquals(
            emptyList(), offenders.distinct().sorted(),
            "these are reachable without the Activity — inject the Koin singletons, derive " +
                "windowRepository from windowControl, or read toolbarButtonSetting from " +
                "CommonUtils.settings directly (R6c1)",
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
}
