package net.bible.sharedui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.MonotonicFrameClock
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onSizeChanged

/** Fix batch 5 F105: the scrollable the volume keys page, last registered wins (the top destination). */
class VolumeScrollRegistry {
    private class Target(val state: ScrollableState, val viewportPx: () -> Int, val frameClock: MonotonicFrameClock?)
    private val targets = ArrayDeque<Target>()
    val hasTarget: Boolean get() = targets.isNotEmpty()

    /** Registers [state]; the returned lambda unregisters it. [viewportPx] sizes one page; [frameClock]
     *  (the registering composition's) lets the page animate from a host scope that has none. */
    fun register(state: ScrollableState, viewportPx: () -> Int, frameClock: MonotonicFrameClock? = null): () -> Unit {
        val entry = Target(state, viewportPx, frameClock)
        targets.addLast(entry)
        return { targets.remove(entry) }
    }

    /** Scrolls the top target by 90% of its viewport; false when nothing is registered. */
    suspend fun scrollPage(down: Boolean): Boolean {
        val t = targets.lastOrNull() ?: return false
        val page = t.viewportPx() * 0.9f
        val delta = if (down) page else -page
        // The host's lifecycle scope has no MonotonicFrameClock, so fall back to the one the target's
        // composition registered; only with neither do we jump.
        val clock = coroutineContext[MonotonicFrameClock] ?: t.frameClock
        if (clock != null) withContext(clock) { t.state.animateScrollBy(delta) } else t.state.scrollBy(delta)
        return true
    }
}

/** Provided by the host; null (goldens, previews, iOS) keeps [volumeScrollTarget] inert. */
val LocalVolumeScrollRegistry = staticCompositionLocalOf<VolumeScrollRegistry?> { null }

/** Marks this scroll container as the one the hardware volume keys page. */
fun Modifier.volumeScrollTarget(state: ScrollableState): Modifier = composed {
    val registry = LocalVolumeScrollRegistry.current
    val clock = rememberCoroutineScope().coroutineContext[MonotonicFrameClock]
    var height by remember { mutableIntStateOf(0) }
    DisposableEffect(registry, state) {
        val unregister = registry?.register(state, { height }, clock)
        onDispose { unregister?.invoke() }
    }
    onSizeChanged { height = it.height }
}

/** [verticalScroll] plus [volumeScrollTarget] on the same state (F105 follow-up). */
fun Modifier.volumeVerticalScroll(state: ScrollState): Modifier = verticalScroll(state).volumeScrollTarget(state)
