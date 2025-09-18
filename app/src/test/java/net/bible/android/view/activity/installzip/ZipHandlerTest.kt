/*
 * Copyright (c) 2025 AndBible contributors.
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
package net.bible.android.view.activity.installzip

import android.app.Activity
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/**
 * Test for ZipHandler exception handling, specifically for FileNotFound exceptions.
 */
class ZipHandlerTest {

    @Test
    fun testFileNotFoundExceptionHandling() = runBlocking {
        // Given: A ZipHandler with a newInputStream lambda that throws FileNotFound
        var finishResultCaptured: Int? = null
        val mockActivity = mockk<Activity>(relaxed = true)
        
        val zipHandler = ZipHandler(
            newInputStream = { throw FileNotFound() },
            updateProgress = { /* no-op */ },
            finish = { result -> finishResultCaptured = result },
            activity = mockActivity
        )

        // When: execute() is called
        zipHandler.execute()

        // Then: The finish callback should be called with RESULT_CANCELED (indicating error)
        assertEquals(Activity.RESULT_CANCELED, finishResultCaptured)
    }
}