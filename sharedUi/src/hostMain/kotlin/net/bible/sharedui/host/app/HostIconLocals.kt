package net.bible.sharedui.host.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import net.bible.sharedui.navigation.LocalCategoryIcon
import net.bible.sharedui.reading.LocalPinIcon
import net.bible.sharedui.reading.host.slots.LocalAgentStatusIcon

/**
 * The icon seams that :sharedUi leaves to the host and that default to `error(...)`. Android
 * provides them in `ProvideAppLocals`; iOS and desktop provide them here, with Material vectors.
 * A seam missing from this list crashes the shared screen that reads it.
 */
internal fun hostIconLocals(): Array<ProvidedValue<*>> = arrayOf(
    LocalCategoryIcon provides { rememberVectorPainter(Icons.Outlined.Book) },
    LocalPinIcon provides { rememberVectorPainter(Icons.Outlined.PushPin) },
    LocalAgentStatusIcon provides { rememberVectorPainter(Icons.Outlined.SmartToy) },
)
