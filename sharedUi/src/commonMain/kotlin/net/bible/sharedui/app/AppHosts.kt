package net.bible.sharedui.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable
import net.bible.sharedui.app.ports.HostPorts
import net.bible.sharedui.strings.Strings
import org.koin.core.Koin

/**
 * Serializable snapshot of the host-held state that must survive `recreate()` and process death.
 * One nullable field per feature that has such state; the owning feature task adds it. [version] is the
 * schema version of the snapshot (a data class needs at least one property).
 */
@Serializable
data class AppHostsState(val version: Int = 1)

/**
 * Owns every feature host for one platform host instance (one Activity lifetime on Android).
 * Constructing it must not touch the database; hosts resolve their collaborators lazily.
 */
class AppHosts(
    @Suppress("unused") private val koin: Koin,
    @Suppress("unused") private val shell: HostShell,
    @Suppress("unused") private val ports: HostPorts,
    @Suppress("unused") private val strings: Strings,
) {
    /** Grows task by task; throws until the first feature host is lifted (I2 Stream A Task 2). */
    @Suppress("UNUSED_PARAMETER")
    fun graphs(navController: NavHostController): Nothing =
        throw NotImplementedError("AppHosts.graphs is wired in I2 Stream A Task 2")

    /** Host-level collectors and sheets, outside the NavHost; grows task by task. */
    @Composable
    fun Overlays() {
    }

    private var current = AppHostsState()

    /** The snapshot to persist; feature hosts contribute their fields as they are lifted in. */
    fun saveState(): AppHostsState = current

    fun restoreState(state: AppHostsState?) {
        current = state ?: AppHostsState()
    }
}
