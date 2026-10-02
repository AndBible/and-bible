package net.bible.sharedui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.MonotonicFrameClock
import kotlin.coroutines.coroutineContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onSizeChanged

/** Fix batch 5 F105: the scrollable the volume keys page, last registered wins (the top destination). */
class VolumeScrollRegistry {
    private val targets = ArrayDeque<Pair<ScrollableState, () -> Int>>()
    val hasTarget: Boolean get() = targets.isNotEmpty()

    /** Registers [state]; the returned lambda unregisters it. [viewportPx] sizes one page. */
    fun register(state: ScrollableState, viewportPx: () -> Int): () -> Unit {
        val entry = state to viewportPx
        targets.addLast(entry)
        return { targets.remove(entry) }
    }

    /** Scrolls the top target by 90% of its viewport; false when nothing is registered. */
    suspend fun scrollPage(down: Boolean): Boolean {
        val (state, viewport) = targets.lastOrNull() ?: return false
        val page = viewport() * 0.9f
        val delta = if (down) page else -page
        // animateScrollBy needs a MonotonicFrameClock; the host's lifecycle scope has none, so jump instead.
        if (coroutineContext[MonotonicFrameClock] != null) state.animateScrollBy(delta) else state.scrollBy(delta)
        return true
    }
}

/** Provided by the host; null (goldens, previews, iOS) keeps [volumeScrollTarget] inert. */
val LocalVolumeScrollRegistry = staticCompositionLocalOf<VolumeScrollRegistry?> { null }

/** Marks this scroll container as the one the hardware volume keys page. */
fun Modifier.volumeScrollTarget(state: ScrollableState): Modifier = composed {
    val registry = LocalVolumeScrollRegistry.current
    var height by remember { mutableIntStateOf(0) }
    DisposableEffect(registry, state) {
        val unregister = registry?.register(state) { height }
        onDispose { unregister?.invoke() }
    }
    onSizeChanged { height = it.height }
}
