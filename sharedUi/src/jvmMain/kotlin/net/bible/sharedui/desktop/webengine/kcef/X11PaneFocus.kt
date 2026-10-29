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
 * @param withLock runs a block under the AWT lock, so the tree query and the focus call are one atomic section
 */
internal class X11PaneFocus(
    private val windowOf: (Component) -> Long?,
    private val children: (Long) -> Collection<Long>,
    private val setFocus: (Long) -> Unit,
    private val log: (String) -> Unit,
    private val withLock: (() -> Unit) -> Unit = { it() },
) {
    @Volatile
    var enabled: Boolean = true
        private set

    private var logged = false
    private var failureLogged = false

    /** Focuses the deepest child X window of [component]'s heavyweight host; no-op when disabled or nothing is found. */
    fun focus(component: Component) {
        if (!enabled) return
        try {
            val root = findHeavyweight(component)?.let(windowOf) ?: return
            withLock {
                val target = deepest(root, children)
                if (!logged) {
                    logged = true
                    log("x11 focus -> $target (depth ${depth(root, target, children)})")
                }
                setFocus(target)
            }
        } catch (t: Throwable) {
            // A per-press failure is not a reason to give up: log the first one and carry on.
            if (!failureLogged) {
                failureLogged = true
                log("X11 pane focus failed (further failures not logged): $t")
            }
        }
    }

    internal fun disable(reason: String) {
        if (!enabled) return
        enabled = false
        log(reason)
    }

    companion object {
        private const val MAX_DEPTH = 16

        private fun depth(root: Long, target: Long, children: (Long) -> Collection<Long>): Int {
            var n = 0
            var cur = root
            while (cur != target && n < MAX_DEPTH) {
                cur = children(cur).firstOrNull() ?: break
                n++
            }
            return n
        }

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
                // XlibUtil.getChildWindows returns only AWT-known children for AWT windows (empty for CEF's), so query X directly.
                val queryTree = Class.forName("sun.awt.X11.XQueryTree")
                val qtNew = queryTree.getConstructor(Long::class.javaPrimitiveType)
                val qtExecute = queryTree.getMethod("execute")
                val qtChildren = queryTree.getMethod("get_children")
                val qtCount = queryTree.getMethod("get_nchildren")
                val qtDispose = queryTree.getMethod("dispose")
                val nativeGetWindow = Class.forName("sun.awt.X11.Native").getDeclaredMethod("getWindow", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                val getDisplay = Class.forName("sun.awt.X11.XToolkit").getMethod("getDisplay")
                val lock = Class.forName("sun.awt.SunToolkit").getMethod("awtLock")
                val unlock = Class.forName("sun.awt.SunToolkit").getMethod("awtUnlock")
                val xlib = Class.forName("sun.awt.X11.XlibWrapper")
                val setInputFocus = xlib.getDeclaredMethod("XSetInputFocus2", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                val sync = xlib.getDeclaredMethod("XSync", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
                val getAttrs = xlib.getDeclaredMethod("XGetWindowAttributes", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                val attrsClass = Class.forName("sun.awt.X11.XWindowAttributes")
                val attrsNew = attrsClass.getConstructor()
                val attrsData = attrsClass.getDeclaredField("pData").apply { isAccessible = true }
                val attrsMapState = attrsClass.getMethod("get_map_state")
                val attrsDispose = attrsClass.getMethod("dispose")
                val handlerUtil = Class.forName("sun.awt.X11.XErrorHandlerUtil")
                val withHandler = handlerUtil.getMethod("WITH_XERROR_HANDLER", Class.forName("sun.awt.X11.XErrorHandler"))
                val restoreHandler = handlerUtil.getMethod("RESTORE_XERROR_HANDLER")
                val ignoreBadWindow = Class.forName("sun.awt.X11.XErrorHandler\$IgnoreBadWindowHandler").getMethod("getInstance").invoke(null)
                var queryFailureLogged = false
                X11PaneFocus(
                    windowOf = { c -> getPeer.invoke(accessor, c)?.let { getWindow.invoke(it) as Long } },
                    children = { w ->
                        lock.invoke(null)
                        try {
                            val q = qtNew.newInstance(w)
                            try {
                                val ok = (qtExecute.invoke(q) as Int) != 0
                                if (!ok && !queryFailureLogged) {
                                    queryFailureLogged = true
                                    log("X11 pane focus: XQueryTree failed for window $w, using it as is")
                                }
                                val ptr = if (!ok) 0L else qtChildren.invoke(q) as Long
                                if (ptr == 0L) emptyList() else List(qtCount.invoke(q) as Int) { nativeGetWindow.invoke(null, ptr, it) as Long }
                            } finally {
                                qtDispose.invoke(q)
                            }
                        } finally {
                            unlock.invoke(null)
                        }
                    },
                    setFocus = { w ->
                        val display = getDisplay.invoke(null) as Long
                        lock.invoke(null)
                        try {
                            // BadWindow is ignored by the handler; BadMatch (a non-viewable window) is avoided by checking the map state
                            // (IsViewable = 2) under the same lock before the request.
                            withHandler.invoke(null, ignoreBadWindow)
                            try {
                                val attrs = attrsNew.newInstance()
                                val viewable = try {
                                    (getAttrs.invoke(null, display, w, attrsData.get(attrs) as Long) as Int) != 0 && (attrsMapState.invoke(attrs) as Int) == 2
                                } finally {
                                    attrsDispose.invoke(attrs)
                                }
                                if (viewable) setInputFocus.invoke(null, display, w, 0L) // CurrentTime; the native call uses RevertToParent
                                sync.invoke(null, display, 0)
                            } finally {
                                restoreHandler.invoke(null)
                            }
                        } finally {
                            unlock.invoke(null)
                        }
                    },
                    log = log,
                    withLock = { block ->
                        lock.invoke(null)
                        try {
                            block()
                        } finally {
                            unlock.invoke(null)
                        }
                    },
                )
            } catch (t: Throwable) {
                off(t.toString())
            }
        }
    }
}
