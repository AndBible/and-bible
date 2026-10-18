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

package net.bible.sharedui.reading

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Source-scan guard: every PUBLIC top-level declaration of the reading stream must carry
 * `@kotlin.native.HiddenFromObjC`.
 *
 * Why: Kotlin/Native 2.4.21's ObjC export asserts in `bridgeReturnType` when it translates a class that
 * the public reading API (e.g. `ReadingNavigator.startKeyChooser(CurrentPage)`, session and host types
 * holding controllers) pulls in as an extra class, so the iOS framework link fails. That failure shows
 * only on the Mac (`linkDebugFrameworkIosSimulatorArm64`); the container's klib compiles stay green
 * (history: commit 9381a75624 "Hide reading-stream declarations from the ObjC export"). Nothing here is
 * used from Swift, and the annotation cannot be set per file, so it is required per declaration.
 *
 * Scope: `sharedui/reading/{bridge,session,host}`, the files moved from `:app` into
 * `net/bible/android/view/activity/page` (Selection, FavouriteLabelIdsCache, screen/ReadingHostHelpers)
 * and the iOS reading files (the `webview` files with Reading in the name, and `WkWebViewJsHost.kt`). Private/internal
 * declarations and typealiases are exempt.
 */
class ObjcExportHiddenGuardTest {
    private val modifier = "(?:(?:public|abstract|open|sealed|data|enum|annotation|inline|value|fun|suspend|tailrec|" +
        "operator|infix|const|lateinit|expect|actual|external|private|internal|protected)\\s+)*"
    private val declaration = Regex("^($modifier)(class|interface|object|fun|val|var)\\b.*")
    private val leadingAnnotation = Regex("^@[\\w.]+(?:\\([^)]*\\))?\\s*")

    private val src = File("src")

    private fun scopedFiles(): List<File> {
        val common = File(src, "commonMain/kotlin/net/bible")
        val reading = File(common, "sharedui/reading")
        val page = File(common, "android/view/activity/page")
        val ios = File(src, "iosMain/kotlin/net/bible/sharedui/webview")
        // Stream A's shared app root: hosts, sessions and ports Swift never calls (I2 merge, same link failure).
        val appRoot = File(common, "sharedui/app").walkTopDown().filter { it.isFile }.toList() +
            listOf(
                "android/view/activity/download/DocumentConfiguration.kt",
                "android/view/activity/download/DownloadProgressBridge.kt",
                "android/view/activity/installzip/InstallZipFlow.kt",
                "android/view/activity/search/EpubSearchModeWire.kt",
                "android/view/activity/search/EpubSearchResultKey.kt",
                "sharedui/DocCategoryMapping.kt",
            ).map { File(common, it) }
        return listOf("bridge", "session", "host").flatMap { File(reading, it).listFiles().orEmpty().toList() } + appRoot +
            listOf(File(page, "Selection.kt"), File(page, "FavouriteLabelIdsCache.kt"), File(page, "screen/ReadingHostHelpers.kt")) +
            ios.listFiles().orEmpty().filter { it.name.contains("Reading") || it.name == "WkWebViewJsHost.kt" }
    }

    /** `file:line declaration` for each public top-level declaration lacking the annotation. */
    internal fun violations(files: List<File>): Pair<List<String>, Int> {
        val missing = mutableListOf<String>()
        var publicCount = 0
        for (file in files.filter { it.extension == "kt" }) {
            val lines = file.readLines()
            for ((i, raw) in lines.withIndex()) {
                if (raw.isEmpty() || raw[0].isWhitespace() || raw.startsWith("//") || raw.startsWith("/*") ||
                    raw.startsWith(" *") || raw.startsWith("*/") || raw.startsWith("}") || raw.startsWith(")")
                ) continue
                var rest = raw
                val annotations = mutableListOf<String>()
                while (true) {
                    val m = leadingAnnotation.find(rest) ?: break
                    annotations += m.value
                    rest = rest.substring(m.value.length)
                }
                val match = declaration.matchEntire(rest) ?: continue
                val mods = match.groupValues[1]
                if (Regex("\\b(private|internal)\\b").containsMatchIn(mods)) continue
                publicCount++
                // Annotations on the lines directly above (stacked annotations, with KDoc or comments between).
                var j = i - 1
                while (j >= 0) {
                    val prev = lines[j]
                    when {
                        prev.startsWith("@") -> annotations += prev
                        prev.isBlank() || prev.startsWith(" ") || prev.startsWith("/*") || prev.startsWith("*/") ||
                            prev.startsWith("//") -> if (prev.isBlank()) break
                        else -> break
                    }
                    j--
                }
                if (annotations.none { it.contains("HiddenFromObjC") }) missing += "${file.path}:${i + 1} ${raw.trim()}"
            }
        }
        return missing to publicCount
    }

    @Test
    fun everyPublicReadingDeclarationIsHiddenFromObjC() {
        val files = scopedFiles()
        assertTrue(files.size > 20, "scope must find the reading files (cwd=${File(".").absolutePath}), got ${files.size}")
        val (missing, publicCount) = violations(files)
        assertTrue(publicCount > 30, "scanner must see the public declarations, saw $publicCount")
        assertTrue(missing.isEmpty(), "public declarations without @kotlin.native.HiddenFromObjC:\n" + missing.joinToString("\n"))
    }

    @Test
    fun scannerFlagsAMissingAnnotationAndAcceptsTheStackedForms() {
        val dir = kotlin.io.path.createTempDirectory("objc-guard").toFile()
        try {
            val f = File(dir, "Sample.kt").apply {
                writeText(
                    """
                    package x

                    /** doc */
                    @kotlin.native.HiddenFromObjC
                    class Hidden

                    /** doc */
                    @OptIn(Foo::class)
                    @kotlin.native.HiddenFromObjC
                    fun stacked() {}

                    @kotlin.native.HiddenFromObjC @OptIn(Foo::class) interface SameLine

                    class Exposed

                    fun exposedFun() {}

                    internal class Fine

                    private fun alsoFine() {}

                    typealias Alias = Int
                    """.trimIndent(),
                )
            }
            val (missing, count) = violations(listOf(f))
            assertTrue(count == 5, "five public declarations expected, got $count")
            assertTrue(missing.size == 2 && missing[0].endsWith("class Exposed") && missing[1].endsWith("fun exposedFun() {}"), "got $missing")
        } finally {
            dir.deleteRecursively()
        }
    }
}
