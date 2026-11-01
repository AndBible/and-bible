/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.cloudsync.canonical

import net.bible.android.database.IdType
import net.bible.android.database.MyUUID
import net.bible.android.database.mydocument.MyDocumentDao
import okio.ByteString.Companion.encodeUtf8

/**
 * Deterministic id for a synced row whose identity is a natural key (issue #3906), so that every device
 * derives the same primary key for the same logical row.
 *
 * Construction, fixed forever (released ids depend on it; pinned by `CanonicalIdsTest.goldenIds`):
 * `sha256(utf8("andbible-canonical-id/v1\n" + table + "\n" + key.joinToString("") { "${it.length}:$it" }))`,
 * first 16 bytes, with the UUID version nibble set to 8 and the RFC 4122 variant bits set.
 * The length prefixes make the key parts unambiguous.
 *
 * @param table the SQL table name, e.g. `"ReadingPlan"`
 * @param key the natural-key columns in their fixed order, as strings
 */
fun canonicalId(table: String, vararg key: String): IdType {
    val input = "andbible-canonical-id/v1\n" + table + "\n" + key.joinToString("") { "${it.length}:$it" }
    val bytes = input.encodeUtf8().sha256().toByteArray().copyOfRange(0, 16)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x80).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    return IdType(MyUUID.fromByteArray(bytes))
}

/** Canonical id of the `ReadingPlan` row for [planCode]. */
fun canonicalReadingPlanId(planCode: String): IdType = canonicalId("ReadingPlan", planCode)

/** Canonical id of the `ReadingPlanStatus` row for [planCode] and [planDay]. */
fun canonicalReadingPlanStatusId(planCode: String, planDay: Int): IdType =
    canonicalId("ReadingPlanStatus", planCode, planDay.toString())

/** Canonical id of the `MyDocument` row for [initials]. */
fun canonicalMyDocumentId(initials: String): IdType = canonicalId("MyDocument", initials)

/** Canonical id of the `MemorizedVerse` row for [kjvOrdinal]. */
fun canonicalMemorizedVerseId(kjvOrdinal: Int): IdType = canonicalId("MemorizedVerse", kjvOrdinal.toString())

/** Canonical id of the `LlmConfiguredModel` row for [providerConfigId] and [modelId]. */
fun canonicalLlmConfiguredModelId(providerConfigId: IdType, modelId: String): IdType =
    canonicalId("LlmConfiguredModel", providerConfigId.toString(), modelId)

/**
 * Id for a new `MyDocument` row with [initials]: its canonical id ([canonicalMyDocumentId]), unless the sync log holds a
 * delete of that id; then a fresh random id, as before #3906.
 *
 * A document's pages are deleted with it by cascade, and cascaded deletes are not logged (the parent's `DELETE` entry
 * stands for them). Re-creating the document under the same id would replace that `DELETE` entry with an `UPSERT`
 * (the log keeps one entry per id), so a peer that has not applied the delete yet would keep the old pages under the
 * re-created document, and later send them back (carried children, the one-time re-send). A fresh id keeps the delete
 * in the log for every peer, old or new. Any logged delete counts, also one received from a peer: patches of different
 * devices are applied in creation-time order, not per device, so a re-creation here could still overtake it elsewhere.
 * The re-created document then has a random id; two devices that do the same diverge again and the receive-side
 * mapping handles that.
 */
suspend fun MyDocumentDao.newMyDocumentId(initials: String): IdType {
    val canonical = canonicalMyDocumentId(initials)
    return if (hasLoggedDelete(canonical)) IdType() else canonical
}
