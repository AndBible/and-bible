/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.common

import fi.sykero.kdom.input.KdomBuilder
import net.bible.sharedlogic.sync.withMonitor

private const val KDOM_BUILDER_POOL_CAPACITY = 32

private val builders = ArrayDeque<KdomBuilder>()

/**
 * Runs [block] with a pooled [KdomBuilder]. Like the `ArrayBlockingQueue(32)` it replaces: a builder is taken
 * from the head or created, and offered back at the tail only if the block returned normally and the pool
 * is not full (a throwing block drops its builder).
 */
fun <R> useKdomBuilder(block: (it: KdomBuilder) -> R): R {
    val builder = withMonitor(builders) { builders.removeFirstOrNull() } ?: KdomBuilder(allowDocType = true)
    val rv = block(builder)
    withMonitor(builders) { if (builders.size < KDOM_BUILDER_POOL_CAPACITY) builders.addLast(builder) }
    return rv
}
