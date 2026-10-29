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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.host.ports

import net.bible.sharedui.app.ports.CompletingDocumentInstallStarter
import net.bible.sharedui.app.ports.DocumentRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.service.installzip.InstallServiceController
import net.bible.service.installzip.InstallSource

/** Starts headless installs without an Android-style service or a screen-owned worker lifetime. */
@kotlin.native.HiddenFromObjC
class IosDocumentInstallStarter(
    private val controller: () -> InstallServiceController = { IosInstallPipeline.controller },
    private val deps: () -> InstallServiceController.JobDeps = { IosInstallPipeline.deps() },
    private val scope: CoroutineScope = IosInstallPipeline.scope,
) : CompletingDocumentInstallStarter {
    override fun start(uris: List<DocumentRef>, action: String?) {
        val sources = sources(uris, action)
        scope.launch(Dispatchers.Default) { controller().enqueue(sources, deps()) }
    }

    override suspend fun startAndAwait(uris: List<DocumentRef>, action: String?) {
        val sources = sources(uris, action)
        withContext(Dispatchers.Default) { controller().enqueue(sources, deps()) }
    }

    private fun sources(uris: List<DocumentRef>, action: String?) = uris.map { ref ->
        // Both metadata fields become Okio destination components; the source URL stays exact.
        val name = lastPathComponent(IosInstallPipeline.filePath(ref.uri))
            .replace('/', '_')?.replace('\\', '_')?.replace(':', '_')?.replace('\u0000', '_')
            ?.takeUnless { it.isBlank() || it == "." || it == ".." } ?: "document"
        val mime = when (name?.substringAfterLast('.', "")?.lowercase()) {
            "epub" -> "application/epub+zip"
            "zip" -> "application/zip"
            "ttf" -> "font/ttf"
            "csv" -> "text/csv"
            else -> null
        }
        InstallSource(ref.uri, action, name, mime, name)
    }

    // There is no foreground service to bind: the process-scoped controller owns every worker.
    override fun hostBound() = Unit
    override fun hostUnbound() = Unit
}
