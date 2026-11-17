package net.bible.desktop

import java.nio.file.Path
import net.bible.sharedcore.platform.desktop.files.InstanceHandOff

/** Returns the launcher's exit code; a failed or timed-out hand-off is never silent. */
internal fun handOffSecondLaunch(dataDir: Path, files: List<Path>, reportFailure: () -> Unit): Int {
    if (InstanceHandOff.send(dataDir, files)) return 0
    reportFailure()
    return 1
}
