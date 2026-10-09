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
package net.bible.service.download

import android.util.Log
import androidx.sqlite.SQLiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import net.bible.android.database.CustomRepository
import net.bible.service.common.CommonUtils.appendUrl
import net.bible.service.common.CommonUtils.md5Hash
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.mybible.MyBibleRepositorySpec
import net.bible.sharedcore.download.CustomRepositoryData
import net.bible.sharedcore.download.CustomRepositoryService
import net.bible.sharedcore.download.ManifestResult
import org.crosswire.jsword.book.install.InstallManager
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Android-side impl of the [CustomRepositoryService] seam, backed by [CustomRepository]'s Room
 * DAO. [validateManifest] is the classic [net.bible.android.view.activity.download.CustomRepositoryEditor]
 * probe (`tryReadManifest`/`checkCanRead`/`readManifest`) lifted verbatim, minus the UI-only
 * `valid`/`loadingIndicator` toggles the Activity used to flip.
 */
class CustomRepositoryServiceImpl : CustomRepositoryService {
    private val dao get() = DatabaseContainer.instance.repoDb.customRepositoryDao()

    override suspend fun list(): List<CustomRepositoryData> = withContext(Dispatchers.IO) {
        dao.all().map { it.toData() }
    }

    /**
     * Classic `handleResult`'s duplicate handling, collapsed to a boolean: an [InstallManager]
     * built-in repository name clash OR a constraint violation (the DB's unique index on `name`)
     * both surface here as `false` rather than throwing or toasting directly -- the host
     * (`CustomRepositoryController.onDuplicate`) decides how to report it. The bundled SQLite driver
     * (D1 Task 17) throws a plain [SQLiteException] ("... constraint failed ...") rather than the
     * framework's `SQLiteConstraintException`, so the violation is recognised by its message; any
     * other SQLite error still propagates.
     */
    override suspend fun upsert(repo: CustomRepositoryData): Boolean = withContext(Dispatchers.IO) {
        if (InstallManager().installers.keys.contains(repo.name)) return@withContext false
        val entity = repo.toEntity()
        try {
            if (repo.id != 0L) dao.update(entity) else dao.insert(entity)
            true
        } catch (e: SQLiteException) {
            if (!isConstraintViolation(e)) throw e
            Log.e(TAG, "Constraint exception", e)
            false
        }
    }

    override suspend fun delete(repo: CustomRepositoryData) = withContext(Dispatchers.IO) {
        dao.delete(repo.toEntity())
    }

    override suspend fun validateManifest(url: String, existingId: Long): ManifestResult {
        if (!url.startsWith("https://")) return ManifestResult.Invalid

        var repo = tryReadManifest(url, existingId)
        if (repo == null) {
            val newUrlStr = appendUrl(url, "manifest.json")
            repo = tryReadManifest(newUrlStr, existingId)
        }
        if (repo == null) {
            val parsedUrl = try { URL(url) } catch (e: MalformedURLException) { null }
            if (parsedUrl != null) {
                val packagesUrl = appendUrl(url, "packages")
                val modsIndexUrl = appendUrl(url, "mods.d.tar.gz")

                val (manifestOk, packagesOk, modsIndexOk) = coroutineScope {
                    awaitAll(
                        async(Dispatchers.IO) { checkCanRead(url) },
                        async(Dispatchers.IO) { checkCanRead(packagesUrl) },
                        async(Dispatchers.IO) { checkCanRead(modsIndexUrl) },
                    )
                }

                if (manifestOk && packagesOk && modsIndexOk) {
                    repo = CustomRepository(
                        id = existingId,
                        name = "${parsedUrl.host}-${md5Hash(url).subSequence(0, 3)}",
                        description = url,
                        manifestUrl = url,
                        host = parsedUrl.host,
                        catalogDirectory = parsedUrl.path,
                        packageDirectory = appendUrl(parsedUrl.path, "packages"),
                        type = "sword-https",
                    )
                }
            }
        }
        return repo?.let { ManifestResult.Valid(it.toData()) } ?: ManifestResult.Invalid
    }

    private suspend fun checkCanRead(urlStr: String): Boolean {
        val manifestUrl = try {
            URL(urlStr)
        } catch (e: MalformedURLException) {
            return false
        }

        return withContext(Dispatchers.IO) {
            val conn =
                try {
                    manifestUrl.openConnection() as HttpsURLConnection
                } catch (e: IOException) {
                    return@withContext false
                }
            val responseCode = try { conn.responseCode } catch (e: IOException) { null }
            return@withContext responseCode == 200
        }
    }

    private suspend fun tryReadManifest(manifestUrlStr: String, existingId: Long): CustomRepository? {
        val manifestUrl = try {
            URL(manifestUrlStr)
        } catch (e: MalformedURLException) {
            return null
        }

        return withContext(Dispatchers.IO) {
            val conn =
                try {
                    manifestUrl.openConnection() as HttpsURLConnection
                } catch (e: IOException) {
                    return@withContext null
                }
            val responseCode = try { conn.responseCode } catch (e: IOException) { null }
            return@withContext if (responseCode == 200) {
                readManifest(conn, existingId)
            } else {
                null
            }
        }
    }

    private fun readManifest(conn: HttpsURLConnection, existingId: Long): CustomRepository? {
        Log.i(TAG, "readManifest")
        val jsonString = String(conn.inputStream.readBytes())
        val json = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            Log.e(TAG, "Error in parsing JSON", e)
            return null
        }
        var repo: CustomRepository? = null

        val type = try { json.getString("type") } catch (e: JSONException) { null }

        if (type == "sword-https") {
            repo = CustomRepository.fromJson(jsonString) ?: return null
        } else {
            val myBibleSpec = try {
                MyBibleRepositorySpec.fromJson(jsonString)
            } catch (e: SerializationException) {
                null
            }
            if (myBibleSpec != null) {
                repo = CustomRepository(
                    name = myBibleSpec.file_name,
                    description = myBibleSpec.description,
                    type = "mybible-https",
                    manifestUrl = myBibleSpec.url,
                )
            }
        }

        if (repo == null) return null
        repo.id = existingId
        Log.i(TAG, "Read manifest ${repo.name}")
        return repo
    }

    companion object {
        private const val TAG = "CustomRepositoryService"
    }
}

/** [CustomRepositoryData] view of a Room [CustomRepository] entity -- all 8 fields, 1:1. */
fun CustomRepository.toData(): CustomRepositoryData = CustomRepositoryData(
    id = id,
    name = name,
    description = description,
    type = type,
    host = host,
    catalogDirectory = catalogDirectory,
    packageDirectory = packageDirectory,
    manifestUrl = manifestUrl,
)

/** Inverse of [toData] -- builds the Room entity from the portable DTO. */
fun CustomRepositoryData.toEntity(): CustomRepository = CustomRepository(
    id = id,
    name = name,
    description = description,
    type = type,
    host = host,
    catalogDirectory = catalogDirectory,
    packageDirectory = packageDirectory,
    manifestUrl = manifestUrl,
)

/** True for SQLite's constraint errors (UNIQUE, FOREIGN KEY, NOT NULL, CHECK...), from either driver. */
internal fun isConstraintViolation(e: SQLiteException): Boolean =
    e.message.orEmpty().contains("constraint failed", ignoreCase = true)
