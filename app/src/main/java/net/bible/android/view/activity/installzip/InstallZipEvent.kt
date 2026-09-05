/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

/**
 * A line of install progress text, posted on `ABEventBus` for whatever screen currently shows the
 * startup/install status line to render.
 *
 * Producers: `DocumentInstallService.postInstallZipEventFor` (the Compose install pipeline) and
 * `EpubOptimization`. Consumers: `StartupActivity` and `StartupComposeActivity`. None of the four
 * is install-zip *screen* code — which is why this outlived the classic `InstallZip` Activity it
 * used to be declared beside, when slice S16 deleted that Activity.
 *
 * **The package is deliberately unchanged.** All four consumers import this by its
 * fully-qualified name `net.bible.android.view.activity.installzip.InstallZipEvent`, so extracting
 * it here rather than relocating it to, say, `net.bible.android.control.event` meant the deletion
 * slice touched not one import line anywhere. Moving it is a separate, reviewable change, not
 * something to fold into a removal.
 */
class InstallZipEvent(val message: String)
