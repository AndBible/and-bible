package net.bible.android.control.backup

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.bible.android.control.page.window.WindowControl
import net.bible.service.cloudsync.CloudSync
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.files.DeliveryDestinations
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** #4027: the Share / save prompt depends on what the platform's [DeliveryDestinations] offers. */
class DestinationPromptTest {
    private val dialogs = AppDialogController()

    private val strings: CoreStrings = Proxy.newProxyInstance(
        CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java),
    ) { _, m, _ -> m.name.removePrefix("get").replaceFirstChar { it.lowercase() } } as CoreStrings

    private inline fun <reified T : Any> stub(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            error("unexpected call of ${T::class.simpleName}.${m.name}")
        } as T

    private inline fun <reified T : Any> blank(): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }

    private fun control() = BackupControl(
        dialogs = dialogs, strings = strings, env = stub<AppEnvironment>(), settings = stub<AppSettings>(),
        windowControl = blank<WindowControl>(), cloudSync = blank<CloudSync>(), notifier = stub<UserNotifier>(),
        dirs = stub<AppDirectories>(),
    )

    private fun labels(request: AppDialogRequest.Options) = request.options.map { (it as SettingsItem.Choice).label }

    @Test
    fun saveOnlyPlatformSkipsThePromptAndSaves() = runTest {
        val result = control().classicDestinationPrompt("t", "m", DeliveryDestinations.SAVE_ONLY)
        assertEquals(SaveOrShare.SAVE, result)
        assertNull(dialogs.pending.value, "no dialog may be shown when there is nothing to choose")
    }

    @Test
    fun androidStyleOffersShareAndPhoneStorage() = runTest {
        val answer = async(start = CoroutineStart.UNDISPATCHED) {
            control().classicDestinationPrompt("t", "m", DeliveryDestinations.SHARE_AND_PHONE_STORAGE)
        }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        assertEquals(listOf("share", "backupPhoneStorage"), labels(head))
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertNull(answer.await())
    }

    @Test
    fun iosStyleOffersShareAndSaveToFiles() = runTest {
        val answer = async(start = CoroutineStart.UNDISPATCHED) {
            control().classicDestinationPrompt("t", "m", DeliveryDestinations.SHARE_AND_FILES)
        }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        assertEquals(listOf("share", "backupSaveToFiles"), labels(head))
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected(head.options[1].value))
        assertEquals(SaveOrShare.SAVE, answer.await())
    }

    @Test
    fun saveOrShareOnSaveOnlyHostNeverCallsTheChooser() = runTest {
        var chooserCalls = 0
        val saved = control().resolveDestinationFor(DeliveryDestinations.SAVE_ONLY, { chooserCalls++; SaveOrShare.SHARE }) {
            error("platform prompt must not run")
        }
        assertEquals(SaveOrShare.SAVE, saved)
        assertEquals(0, chooserCalls)
    }

    @Test
    fun saveOrShareOnChoosingHostStillUsesTheChooser() = runTest {
        val shared = control().resolveDestinationFor(DeliveryDestinations.SHARE_AND_FILES, { SaveOrShare.SHARE }) { null }
        assertEquals(SaveOrShare.SHARE, shared)
    }
}
