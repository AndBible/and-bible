package net.bible.service.db

import java.nio.file.Files

actual fun newTestDir(durable: Boolean): String = Files.createTempDirectory("sharedlogic-test-").toString().also {
    if (!durable) TestSqliteDurability.registerDirectory(it)
}
