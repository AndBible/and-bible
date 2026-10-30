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
package net.bible.desktop

import net.bible.sharedcore.platform.desktop.secrets.DesktopSecretStore
import net.bible.sharedcore.platform.desktop.secrets.JavaKeyringBackend
import net.bible.sharedcore.platform.desktop.secrets.KeyringBackend
import java.io.File

/**
 * Developer self-test of the OS credential store: `--selftest-secrets <save|read|delete> [value]` as the first program
 * argument, honoured only when system property `andbible.selftest` is `true` (the packaged app never sets it).
 * Talks to the raw backend (not the degrading store) so a failure is visible. Account `selftest`, service `AndBible`.
 */
object SecretsSelfTest {
    const val FLAG = "--selftest-secrets"
    const val ACCOUNT = "selftest"

    /** Returns the process exit code, or null when [args] are not a (enabled) self-test request. */
    fun run(
        args: List<String>,
        enabled: Boolean,
        backend: () -> KeyringBackend = { JavaKeyringBackend() },
        out: (String) -> Unit,
    ): Int? {
        if (!enabled || args.firstOrNull() != FLAG) return null
        val action = args.getOrNull(1).orEmpty()
        val (result, code) = try {
            when (action) {
                "save" -> { backend().set(DesktopSecretStore.SERVICE, ACCOUNT, args.getOrNull(2) ?: "selftest-value"); "ok" to 0 }
                "read" -> (backend().get(DesktopSecretStore.SERVICE, ACCOUNT)?.let { "value=$it" } ?: "absent") to 0
                "delete" -> { backend().delete(DesktopSecretStore.SERVICE, ACCOUNT); "ok" to 0 }
                else -> "error: unknown action '$action' (save|read|delete)" to 1
            }
        } catch (e: Throwable) {
            "error: ${e.message ?: e.toString()}" to 1
        }
        out("selftest-secrets $action: $result")
        return code
    }

    /** Entry used by `main()`: prints to stdout and appends to the file named by `andbible.selftest.out`. */
    fun runFromMain(args: Array<String>): Int? = run(args.toList(), System.getProperty("andbible.selftest") == "true") { line ->
        println(line)
        System.getProperty("andbible.selftest.out")?.let { File(it).appendText(line + "\n") }
    }
}
