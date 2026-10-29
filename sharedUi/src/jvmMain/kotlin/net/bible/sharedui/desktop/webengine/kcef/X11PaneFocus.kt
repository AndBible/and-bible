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
package net.bible.sharedui.desktop.webengine.kcef

import java.awt.Component
import java.awt.Container

/**
 * Gives CEF the X11 keyboard focus after a press in a pane. Under a window manager `browser.setFocus(true)` alone leaves the
 * X focus on AWT's focus proxy, so the page gets no keys; focusing CEF's innermost child X window directly does. The decision
 * logic takes injected lambdas so it is testable; [create] wires AWT's own Xlib binding by reflection. Any failure logs one line
 * and turns the feature off; [focus] never throws.
 *
 * @param windowOf X window id of a heavyweight, displayable AWT component, or null
 * @param children child X windows of a window, outermost first
 * @param setFocus performs XSetInputFocus(window, RevertToParent, CurrentTime)
 */
internal class X11PaneFocus(
    private val windowOf: (Component) -> Long?,
    private val children: (Long) -> Collection<Long>,
    private val setFocus: (Long) -> Unit,
    private val log: (String) -> Unit,
) {
    @Volatile
    var enabled: Boolean = true
        private set

    /** Focuses the deepest child X window of [component]'s heavyweight host; no-op when disabled or nothing is found. */
    fun focus(component: Component) {
        if (!enabled) return
        try {
            val root = findHeavyweight(component)?.let(windowOf) ?: return
            setFocus(deepest(root, children))
        } catch (t: Throwable) {
            disable("X11 pane focus failed, disabled: $t")
        }
    }

    internal fun disable(reason: String) {
        if (!enabled) return
        enabled = false
        log(reason)
    }

    companion object {
        private const val MAX_DEPTH = 16

        /** Follows the first child at each level (CEF has one chain of children) down to the deepest window; [root] if none. */
        fun deepest(root: Long, children: (Long) -> Collection<Long>): Long {
            var current = root
            repeat(MAX_DEPTH) {
                current = children(current).firstOrNull() ?: return current
            }
            return current
        }

        /** [c] itself or the first descendant (breadth-first) that is heavyweight and displayable. */
        fun findHeavyweight(c: Component): Component? {
            val queue = ArrayDeque<Component>().apply { add(c) }
            while (queue.isNotEmpty()) {
                val x = queue.removeFirst()
                if (!x.isLightweight && x.isDisplayable) return x
                if (x is Container) queue.addAll(x.components)
            }
            return null
        }

        /** Resolves the JDK 17 X11 internals once; a disabled instance (one log line) when this is not X11 or access fails. */
        fun create(log: (String) -> Unit): X11PaneFocus {
            fun off(why: String) = X11PaneFocus({ null }, { emptyList() }, {}, log).also { it.disable("X11 pane focus off: $why") }
            if (!System.getProperty("os.name", "").startsWith("Linux")) return off("not Linux")
            return try {
                val toolkit = java.awt.Toolkit.getDefaultToolkit()
                if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return off("toolkit is ${toolkit.javaClass.name}")
                val accessor = Class.forName("sun.awt.AWTAccessor").getMethod("getComponentAccessor").invoke(null)
                val getPeer = Class.forName("sun.awt.AWTAccessor\$ComponentAccessor").getMethod("getPeer", Component::class.java)
                val getWindow = Class.forName("sun.awt.X11.XBaseWindow").getMethod("getWindow")
                val childWindows = Class.forName("sun.awt.X11.XlibUtil").getDeclaredMethod("getChildWindows", Long::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                val getDisplay = Class.forName("sun.awt.X11.XToolkit").getMethod("getDisplay")
                val lock = Class.forName("sun.awt.SunToolkit").getMethod("awtLock")
                val unlock = Class.forName("sun.awt.SunToolkit").getMethod("awtUnlock")
                val xlib = Class.forName("sun.awt.X11.XlibWrapper")
                val setInputFocus = xlib.getDeclaredMethod("XSetInputFocus2", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                val flush = xlib.getDeclaredMethod("XFlush", Long::class.javaPrimitiveType).apply { isAccessible = true }
                X11PaneFocus(
                    windowOf = { c -> getPeer.invoke(accessor, c)?.let { getWindow.invoke(it) as Long } },
                    children = { w -> (childWindows.invoke(null, w) as Collection<*>).map { it as Long } },
                    setFocus = { w ->
                        val display = getDisplay.invoke(null) as Long
                        lock.invoke(null)
                        try {
                            setInputFocus.invoke(null, display, w, 0L) // CurrentTime; the native call uses RevertToParent
                            flush.invoke(null, display)
                        } finally {
                            unlock.invoke(null)
                        }
                    },
                    log = log,
                )
            } catch (t: Throwable) {
                off(t.toString())
            }
        }
    }
}
