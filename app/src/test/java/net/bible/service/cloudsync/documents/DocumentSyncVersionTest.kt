/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndBible. If not, see <http://www.gnu.org/licenses/>.
 */

package net.bible.service.cloudsync.documents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSyncVersionTest {
    @Test fun numericNewer() {
        assertTrue(DocumentSync.versionIsNewer("2.0", "1.0"))
        assertTrue(DocumentSync.versionIsNewer("1.1", "1.0"))
        assertTrue(DocumentSync.versionIsNewer("1.0.1", "1.0"))
    }

    @Test fun numericNotNewer() {
        assertFalse(DocumentSync.versionIsNewer("1.0", "2.0"))
        assertFalse(DocumentSync.versionIsNewer("1.0", "1.1"))
    }

    @Test fun equalNumericIsNotNewer() {
        assertFalse(DocumentSync.versionIsNewer("1.0", "1.0"))
        assertFalse(DocumentSync.versionIsNewer("2.6", "2.6"))
    }

    @Test fun equalNonNumericIsNotNewer() {
        // The short-circuit on raw string equality is what stops a non-parseable version from
        // looking like a perpetual change: equal strings are never "newer", so the push skip-guard
        // skips re-uploading and the resolver doesn't propagate a phantom upgrade.
        assertFalse(DocumentSync.versionIsNewer("1.0-beta", "1.0-beta"))
        assertFalse(DocumentSync.versionIsNewer("2024-01-01", "2024-01-01"))
        assertFalse(DocumentSync.versionIsNewer("", ""))
        assertFalse(DocumentSync.versionIsNewer("abc", "abc"))
    }

    @Test fun unparseableDifferingIsConservativelyNotNewer() {
        // No reliable order for genuinely unparseable versions → treat a differing one as
        // not-newer rather than guess. Avoids both phantom upgrades and inverted push skip-guards.
        assertFalse(DocumentSync.versionIsNewer("abc", "1.0"))
        assertFalse(DocumentSync.versionIsNewer("1.0", "abc"))
        assertFalse(DocumentSync.versionIsNewer("1.0-beta", "1.0")) // trailing non-digit → unparseable
        assertFalse(DocumentSync.versionIsNewer("", "1.0"))
    }

    @Test fun dateVersionsParseViaLenientSeparators() {
        // JSword's Version pattern treats any character as a part separator, so digit-grouped
        // date strings (common in MyBible/MySword/eSword) parse and compare correctly — a genuine
        // upgrade in a non-dotted version still propagates.
        assertTrue(DocumentSync.versionIsNewer("2024-06-01", "2024-01-01"))
        assertFalse(DocumentSync.versionIsNewer("2024-01-01", "2024-06-01"))
        assertFalse(DocumentSync.versionIsNewer("2024-06-01", "2024-06-01"))
    }
}
