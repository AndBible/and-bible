package net.bible.shareddata

import java.nio.file.Files

actual fun testDatabaseDir(): String = Files.createTempDirectory("shareddata-test-").toString()
