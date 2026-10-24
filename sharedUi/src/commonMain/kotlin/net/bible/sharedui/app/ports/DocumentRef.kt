@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

import kotlin.jvm.JvmInline

/** A platform document handle. Android: `Uri.parse(ref.uri)` / `DocumentRef(uri.toString())`. */
@JvmInline
@kotlin.native.HiddenFromObjC
value class DocumentRef(val uri: String)
