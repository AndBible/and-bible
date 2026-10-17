package net.bible.service.db

import java.nio.file.Files

actual fun newTestDir(): String = Files.createTempDirectory("sharedlogic-test-").toString()
