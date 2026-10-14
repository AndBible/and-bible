package net.bible.test

import android.os.Looper
import net.bible.android.view.activity.page.ReadingCommands
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * A workspace switch reads off the main thread and applies on it, so a test that triggers one (a host
 * event, a resume) waits here before asserting its effect. Bounded; waits on the in-flight counter, not on time.
 */
fun settleWorkspaceSwitches() {
    val deadline = System.nanoTime() + 30_000_000_000L
    while (ReadingCommands.workspaceSwitchesInFlight.get() > 0 && System.nanoTime() < deadline) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
        Thread.yield()
    }
    check(ReadingCommands.workspaceSwitchesInFlight.get() == 0) { "a workspace switch did not settle" }
}
