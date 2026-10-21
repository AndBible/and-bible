package net.bible.sharedui.ios.startup.legacy

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.service.legacymigration.extras.LegacyExtrasApplier
import net.bible.service.legacymigration.extras.LegacyExtrasFile
import net.bible.service.legacymigration.extras.LegacyNotice
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.keyed.StringTable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosLegacyNoticesTest {
    private val dir: Path = (NSTemporaryDirectory() + "legacy-notices-" + NSUUID().UUIDString).toPath()
    private val fs = FileSystem.SYSTEM

    @AfterTest fun cleanUp() { fs.deleteRecursively(dir) }

    private class MapSettings(val failOnSet: Boolean = false) : AppSettings {
        val map = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = map[key] as? String ?: default
        override fun getLong(key: String, default: Long) = map[key] as? Long ?: default
        override fun getInt(key: String, default: Int) = map[key] as? Int ?: default
        override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun getDouble(key: String, default: Double) = map[key] as? Double ?: default
        override fun getFloat(key: String, default: Float) = map[key] as? Float ?: default
        override fun setString(key: String, value: String?) { check(!failOnSet) { "settings store not writable" }; map[key] = value }
        override fun setLong(key: String, value: Long?) { map[key] = value }
        override fun setInt(key: String, value: Int?) { map[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { map[key] = value }
        override fun setDouble(key: String, value: Double?) { map[key] = value }
        override fun setFloat(key: String, value: Float?) { map[key] = value }
    }

    private class NoPrefs : DeviceLocalPrefs {
        override fun getString(key: String, default: String?) = default
        override fun getBoolean(key: String, default: Boolean) = default
        override fun setString(key: String, value: String) = Unit
        override fun setBoolean(key: String, value: Boolean) = Unit
        override fun remove(key: String) = Unit
    }

    private val table = StringTable(
        tag = "en",
        text = { "T:$it" },
        format = { key, args -> "F:$key:${args.joinToString(",")}" },
        plural = { n, _ -> n },
        array = { emptyList() },
    )

    private fun applier(): LegacyExtrasApplier {
        fs.createDirectories(dir)
        fs.write(dir / LegacyExtrasFile.NAME) {
            writeUtf8(
                """{"version":1,"sync":{"adapter":"NEXT_CLOUD","serverUrl":"https://c.example/dav","username":"u","folderPath":"AndBible","interval":null,"enabledCategories":[],"hadPassword":true},""" +
                    """"icloudSyncWasEnabled":true,"unsentSyncPatches":true,"deviceLocalPrefs":[],"skippedUnverifiedBookmarks":2}""",
            )
        }
        return LegacyExtrasApplier(fs, dir, NoPrefs())
    }

    @Test
    fun showsEachPendingNoticeOnceInOrder() = runBlocking {
        val applier = applier()
        val settings = MapSettings()
        val dialogs = AppDialogController()
        val shown = mutableListOf<String>()
        val responder = launch(Dispatchers.Default) {
            while (true) {
                dialogs.pending.value?.let { p ->
                    val m = p.request as AppDialogRequest.Message
                    assertEquals(null, m.title)
                    assertEquals("T:okay", m.confirmText)
                    shown += m.message
                    dialogs.respond(p.id, AppDialogResult.Ok)
                }
                delay(5)
            }
        }
        IosLegacyNotices.showPending(dialogs, settings, table, applier)
        IosLegacyNotices.showPending(dialogs, settings, table, applier)
        responder.cancelAndJoin()

        assertEquals(
            listOf("T:legacy_notice_icloud", "T:legacy_notice_nextcloud", "T:legacy_notice_nextcloud_unsent", "F:legacy_notice_bookmarks_skipped:2"),
            shown,
        )
        assertEquals("NEXT_CLOUD", settings.map["sync_adapter"])
        assertTrue(applier.pendingNotices().isEmpty())
    }

    @Test
    fun failureToApplyShowsAndMarksNothing() = runBlocking {
        val applier = applier()
        val dialogs = AppDialogController()
        IosLegacyNotices.showPending(dialogs, MapSettings(failOnSet = true), table, applier)
        assertEquals(null, dialogs.pending.value)
        assertEquals(LegacyNotice.entries, applier.pendingNotices())
    }

    @Test
    fun dialogNotAnsweredLeavesNoticePending() = runBlocking {
        val applier = applier()
        val dialogs = AppDialogController()
        val job = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            IosLegacyNotices.showPending(dialogs, MapSettings(), table, applier)
        }
        withTimeout(10_000) { while (dialogs.pending.value == null) delay(5) }
        job.cancelAndJoin() // process killed / host gone while the first dialog is open
        assertEquals(LegacyNotice.entries, applier.pendingNotices())
    }
}
