package net.bible.sharedui.app.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.PlatformText
import net.bible.sharedui.app.ports.PluralKey
import net.bible.sharedui.strings.iosStrings
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudDocumentsHostTest {
    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    /** Records every call and answers a string that names it, so a test can assert exactly what was asked. */
    private class RecordingText : PlatformText {
        val calls = mutableListOf<String>()
        override fun formatFileSize(bytes: Long): String {
            calls += "size($bytes)"
            return "SIZE[$bytes]"
        }
        override fun plural(key: PluralKey, count: Int, vararg args: Any): String {
            calls += "plural($key,$count,${args.toList()})"
            return "PLURAL[$key,$count,${args.toList()}]"
        }
    }

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            error("unexpected ${m.name}")
        } as T

    private val strings = iosStrings("en")
    private val text = RecordingText()

    private val host = CloudDocumentsHost(
        shell = shell,
        strings = strings,
        platformText = text,
        platform = unused(),
        helpDialogs = unused(),
        syncStarter = unused(),
        appSettingsProvider = { error("unused") },
        documentSyncSettingsProvider = { error("unused") },
        documentSyncProvider = { error("unused") },
        cloudSyncProvider = { error("unused") },
        restoredFilters = { null },
        consumeRestoredFilters = { },
    )

    @Test
    fun countLabel() {
        assertEquals(strings.cloudDocSyncNowCountNone, host.countLabel(0, 4096L))
        assertEquals(emptyList(), text.calls.filter { it.startsWith("plural") })

        assertEquals(
            text.plural(PluralKey.CLOUD_DOC_SYNC_NOW_COUNT_SIZE, 3, 3, text.formatFileSize(2048)),
            host.countLabel(3, 2048L),
        )
        assertEquals(text.plural(PluralKey.CLOUD_DOC_SYNC_NOW_COUNT, 3, 3), host.countLabel(3, null))
        assertEquals(text.plural(PluralKey.CLOUD_DOC_SYNC_NOW_COUNT, 3, 3), host.countLabel(3, 0L))
    }
}
