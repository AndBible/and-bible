@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.ios.ports

import net.bible.sharedui.app.bookmark.ResourceLabelIcons

/** iOS uses the shared copies of Android's label and bookmark glyphs. */
@kotlin.native.HiddenFromObjC
class IosLabelIcons : ResourceLabelIcons()
