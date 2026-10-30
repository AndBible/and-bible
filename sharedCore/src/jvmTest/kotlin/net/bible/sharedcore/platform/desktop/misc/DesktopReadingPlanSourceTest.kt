package net.bible.sharedcore.platform.desktop.misc

import java.net.URLClassLoader
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopReadingPlanSourceTest {
    @Test fun missingReadingPlanFolderIsEmpty() {
        URLClassLoader(emptyArray(), null).use { loader ->
            val source = DesktopReadingPlanSource(loader)
            assertEquals(emptyList(), source.builtInPlanCodes())
            assertNull(source.openBuiltInPlan("missing"))
        }
    }

    @Test fun bundledPlansListedFromJar() {
        val jar = Files.createTempFile("plans", ".jar")
        JarOutputStream(Files.newOutputStream(jar)).use { out ->
            for ((name, content) in mapOf("readingplan/index.txt" to "sample\n", "readingplan/sample.properties" to "name=Test\n")) {
                out.putNextEntry(JarEntry(name))
                out.write(content.toByteArray(Charsets.ISO_8859_1))
                out.closeEntry()
            }
        }
        URLClassLoader(arrayOf(jar.toUri().toURL()), null).use { loader ->
            val source = DesktopReadingPlanSource(loader)
            assertEquals(listOf("sample"), source.builtInPlanCodes())
            assertEquals("name=Test\n", source.openBuiltInPlan("sample"))
            assertNull(source.openBuiltInPlan("../sample"))
        }
    }
}
