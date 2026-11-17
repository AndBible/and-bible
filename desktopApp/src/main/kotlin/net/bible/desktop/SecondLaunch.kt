package net.bible.desktop

import java.nio.file.Path
import net.bible.sharedcore.platform.desktop.files.InstanceHandOff
import net.bible.sharedui.strings.keyed.StringTable

internal fun reportSecondLaunchFailure(
    strings: StringTable,
    headless: Boolean = java.awt.GraphicsEnvironment.isHeadless(),
    stderr: (String) -> Unit = { System.err.println(it) },
    present: (String, String) -> Unit = ::showEngineFailureDialog,
) {
    val message = strings.text("desktop_activation_failed")
    stderr(message)
    if (!headless) runCatching { present(strings.text("error_occurred"), message) }
}

/** Returns the launcher's exit code; a failed or timed-out hand-off is never silent. */
internal fun handOffSecondLaunch(dataDir: Path, files: List<Path>, reportFailure: () -> Unit): Int {
    if (InstanceHandOff.send(dataDir, files)) return 0
    reportFailure()
    return 1
}
