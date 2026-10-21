package net.bible.sharedui.ios.startup.legacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.service.legacymigration.control.LegacyMigrationControl
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.ios.startup.LEGACY_SETTLED_KEY
import net.bible.sharedui.ios.startup.legacySettled
import net.bible.sharedui.strings.keyed.StringTable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosLegacyDataImportTest {
    private val dir: Path = (NSTemporaryDirectory() + "legacy-import-" + NSUUID().UUIDString).toPath()
    private val fs = FileSystem.SYSTEM
    private val settings = IosDeviceSettings()

    @AfterTest fun cleanUp() {
        fs.deleteRecursively(dir)
        settings.remove(LEGACY_SETTLED_KEY)
    }

    private val table = StringTable(
        tag = "en",
        text = { "T:$it" },
        format = { key, args -> "F:$key:${args.joinToString(",")}" },
        plural = { n, _ -> n },
        array = { emptyList() },
    )

    private fun import(dialogs: AppDialogController) =
        IosLegacyDataImport(LegacyMigrationControl(fs, dir), dialogs, { table }, settings)

    private suspend fun answeringConfirm(dialogs: AppDialogController, answer: AppDialogResult, body: suspend () -> Unit): List<AppDialogRequest> {
        val seen = mutableListOf<AppDialogRequest>()
        val responder = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                dialogs.pending.value?.let { p ->
                    seen += p.request
                    dialogs.respond(p.id, if (p.request is AppDialogRequest.Confirm) answer else AppDialogResult.Ok)
                }
                delay(5)
            }
        }
        body()
        responder.cancelAndJoin()
        return seen
    }

    @Test
    fun confirmYesRequestsImportAndClearsSettled() = runBlocking {
        settings.putBoolean(LEGACY_SETTLED_KEY, true)
        val dialogs = AppDialogController()
        val control = LegacyMigrationControl(fs, dir)
        val seen = answeringConfirm(dialogs, AppDialogResult.Ok) { import(dialogs).confirmAndRequest() }

        assertTrue(control.importRequested())
        assertFalse(legacySettled(settings))
        assertEquals(2, seen.size)
        assertEquals("T:legacy_import_confirm", (seen[0] as AppDialogRequest.Confirm).message)
        assertEquals("T:legacy_import_reopen", (seen[1] as AppDialogRequest.Message).message)
    }

    @Test
    fun confirmNoChangesNothing() = runBlocking {
        settings.putBoolean(LEGACY_SETTLED_KEY, true)
        val dialogs = AppDialogController()
        val seen = answeringConfirm(dialogs, AppDialogResult.Cancel) { import(dialogs).confirmAndRequest() }

        assertFalse(LegacyMigrationControl(fs, dir).importRequested())
        assertTrue(legacySettled(settings))
        assertEquals(1, seen.size)
    }
}
