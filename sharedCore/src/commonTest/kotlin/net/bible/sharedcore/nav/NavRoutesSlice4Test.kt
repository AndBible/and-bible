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
package net.bible.sharedcore.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavRoutesSlice4Test {

    @Test
    fun myDocumentPagesCarriesAllThreeArgumentsAndRoundTripsThem() {
        val route = NavRoutes.myDocumentPages(
            documentId = "0191f4b2-1c3d-7000-8000-000000000001",
            documentInitials = "MyDoc_1",
            documentName = "Notes on Romans & Galatians",
        )
        assertTrue(route.startsWith("documents/pages?"), "unexpected base: $route")
        assertEquals(
            "Notes on Romans & Galatians",
            NavRoutes.decodeArg(route.substringAfter("${NavRoutes.ARG_DOCUMENT_NAME}=").substringBefore("&")),
        )
    }

    @Test
    fun downloadOmitsEveryFlagThatIsFalseAndEveryArgumentThatIsNull() {
        assertEquals("documents/download", NavRoutes.download())
    }

    @Test
    fun downloadEmitsOnlyTheFlagsThatAreSet() {
        val route = NavRoutes.download(firstDownload = true, downloadRecommended = true)
        assertTrue(route.contains("${NavRoutes.ARG_FIRST_DOWNLOAD}=true"), route)
        assertTrue(route.contains("${NavRoutes.ARG_DOWNLOAD_RECOMMENDED}=true"), route)
        assertTrue(!route.contains(NavRoutes.ARG_DOWNLOAD_ADDONS), "an unset flag must not appear: $route")
    }

    @Test
    fun downloadRoundTripsADocumentIdsJsonPayload() {
        val json = """[{"initials":"KJV","name":"King James Version","abbreviation":"KJV","language":"en"}]"""
        val route = NavRoutes.download(documentIds = json)
        assertEquals(
            json,
            NavRoutes.decodeArg(route.substringAfter("${NavRoutes.ARG_DOCUMENT_IDS}=").substringBefore("&")),
        )
    }

    @Test
    fun customRepositoryEditorRoundTripsARepositoryId() {
        val route = NavRoutes.customRepositoryEditor(42L)
        assertEquals(
            "42",
            NavRoutes.decodeArg(route.substringAfter("${NavRoutes.ARG_REPOSITORY_ID}=").substringBefore("&")),
        )
    }

    @Test
    fun customRepositoryEditorOmitsTheIdEntirelyForANewRepository() {
        // Absent means NEW (plan D9). The id must NOT be emitted present-and-empty: whether the
        // fork hands `repositoryId=` back as "" or as defaultValue = null is unverified, and the
        // arm would have to guess.
        assertEquals("documents/repositories/edit", NavRoutes.customRepositoryEditor(null))
    }
}
