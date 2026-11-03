package net.bible.desktop.webkitgtk

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Contract of the WebKitGTK .deb's desktop launcher template, next to the KCEF one it is derived from. */
class LinuxPackagingTest {
    private val template = File("packaging/linux/org.andbible.AndBible.WebKitGtk.desktop")
    private val kcefTemplate = File("../desktopApp/packaging/linux/org.andbible.AndBible.desktop")

    private fun entries(file: File): List<Pair<String, String>> {
        assertTrue(file.isFile, "Missing $file")
        return file.readLines().filter { it.isNotBlank() && !it.startsWith("[") }.map {
            it.substringBefore('=') to it.substringAfter('=')
        }
    }

    private fun value(file: File, key: String): String =
        entries(file).single { it.first == key }.second

    @Test
    fun launcherIdentityIsDistinctFromKcefPackage() {
        assertEquals("AndBible (WebKitGTK)", value(template, "Name"))
        assertEquals("/opt/andbible-webkitgtk/bin/andbible-webkitgtk %F", value(template, "Exec"))
        assertEquals("/opt/andbible-webkitgtk/lib/andbible-webkitgtk.png", value(template, "Icon"))
        assertEquals("net-bible-desktop-webkitgtk-AppMainKt", value(template, "StartupWMClass"))
    }

    @Test
    fun hasExactlyOneExecAndNoComments() {
        assertEquals(1, entries(template).count { it.first == "Exec" })
        assertTrue(template.readLines().none { it.trimStart().startsWith("#") }, "template must be comment-free")
    }

    @Test
    fun mimeTypesMatchTheKcefLauncher() {
        fun mimes(f: File) = value(f, "MimeType").split(';').filter { it.isNotEmpty() }.toSet()
        assertTrue(mimes(kcefTemplate).isNotEmpty())
        assertEquals(mimes(kcefTemplate), mimes(template))
    }

    @Test
    fun otherFieldsMatchTheKcefLauncher() {
        for (key in listOf("Type", "Comment", "Terminal", "Categories")) {
            assertEquals(value(kcefTemplate, key), value(template, key), key)
        }
    }
}
