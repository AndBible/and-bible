package net.bible.desktop.wintoast

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import net.bible.sharedcore.log.Log
import net.bible.sharedui.desktop.assembly.DesktopNotifierSource
import java.nio.file.Files
import java.nio.file.Path

data class WindowsIdentityResult(val available: Boolean, val aumid: String?)

private fun serverExecutable(command: String): String? =
    Regex("^\\\"([^\\\"]+)\\\"\\s+-ToastActivated$").matchEntire(command)?.groupValues?.get(1)

internal fun applyWindowsIdentity(
    installDir: Path?,
    packaged: () -> Boolean,
    write: (RegistryValue) -> Unit,
    setProcessId: (String) -> Unit,
    log: (String) -> Unit,
    read: (RegistryValue) -> String? = { null },
    exists: (String) -> Boolean = { false },
): WindowsIdentityResult = try {
    if (packaged()) WindowsIdentityResult(true, null)
    else {
        if (installDir != null) {
            val iconPath = installDir.resolve("app/resources/andbible.ico").toString()
            val executable = installDir.resolve("AndBible.exe").toString()
            val plan = aumidRegistryPlan(false, iconPath, executable)
            val existingIcon = read(plan.first { it.name == "IconUri" })
            val server = plan.last()
            val existingServer = read(server)
            val existingExecutable = existingServer?.let(::serverExecutable)
            val registeredTargetsExist = existingIcon != null && existingExecutable != null &&
                exists(existingIcon) && exists(existingExecutable)
            if (!registeredTargetsExist || existingExecutable == executable) plan.forEach(write)
        }
        setProcessId(ANDBIBLE_AUMID)
        WindowsIdentityResult(true, ANDBIBLE_AUMID)
    }
} catch (e: Exception) {
    log("Windows app identity failed: ${e.message}")
    WindowsIdentityResult(false, null)
} catch (e: LinkageError) {
    log("Windows app identity failed: ${e.message}")
    WindowsIdentityResult(false, null)
}

private interface PackageApi : StdCallLibrary {
    fun GetCurrentPackageFullName(length: IntByReference, name: Pointer?): Int
}
private interface ShellApi : StdCallLibrary {
    fun SetCurrentProcessExplicitAppUserModelID(id: com.sun.jna.WString): Int
}

object WindowsAppIdentity {
    fun isPackaged(): Boolean {
        val api = Native.load("kernel32", PackageApi::class.java, W32APIOptions.DEFAULT_OPTIONS)
        val status = api.GetCurrentPackageFullName(IntByReference(0), null)
        return when (status) {
            15700 -> false
            0, 122 -> true
            else -> error("GetCurrentPackageFullName failed: $status")
        }
    }

    fun resolve(installDir: Path?): WindowsIdentityResult = applyWindowsIdentity(
        installDir, ::isPackaged,
        { value ->
            val key = value.key.removePrefix("HKCU\\")
            Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key)
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, value.name, value.value)
        },
        { id ->
            val api = Native.load("shell32", ShellApi::class.java, W32APIOptions.DEFAULT_OPTIONS)
            val hr = api.SetCurrentProcessExplicitAppUserModelID(com.sun.jna.WString(id))
            check(hr >= 0) { "SetCurrentProcessExplicitAppUserModelID failed: $hr" }
        },
        { Log.w("WindowsToast", it) },
        read = { value ->
            val key = value.key.removePrefix("HKCU\\")
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, value.name)) {
                Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, key, value.name)
            } else null
        },
        exists = { Files.exists(Path.of(it)) },
    )

    fun apply(installDir: Path): String? = resolve(installDir).aumid
}

fun windowsToastNotifierSource(aumid: String?): DesktopNotifierSource =
    DesktopNotifierSource(true) { WindowsToastNotifier(aumid) }

fun windowsToastNotifierSource(result: WindowsIdentityResult): DesktopNotifierSource =
    DesktopNotifierSource(result.available) { WindowsToastNotifier(result.aumid) }
