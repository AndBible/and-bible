package net.bible.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/** `version.properties` at the repo root is the single version source; the manifest must not carry its own. */
class VersionSourceTest {
    private fun repoFile(path: String): File =
        listOf(File("../$path"), File(path)).first { it.exists() }

    private val props = Properties().apply { repoFile("version.properties").inputStream().use { load(it) } }

    @Test
    fun versionCodeComesFromVersionProperties() {
        assertEquals(props.getProperty("versionCode").toInt(), net.bible.android.activity.BuildConfig.VERSION_CODE)
    }

    @Test
    fun versionNameStartsWithMajorMinorAndCode() {
        val expected = "${props.getProperty("versionMajorMinor")}.${props.getProperty("versionCode")}"
        assertTrue(net.bible.android.activity.BuildConfig.VERSION_NAME, net.bible.android.activity.BuildConfig.VERSION_NAME.startsWith(expected))
    }

    @Test
    fun manifestDoesNotDeclareVersion() {
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("android:versionCode"))
        assertFalse(manifest.contains("android:versionName"))
    }
}
