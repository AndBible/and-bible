package net.bible.desktop.webkitgtk

/** WebKitGTK `WebKitPolicyDecisionType` values the pane reacts to. */
internal const val POLICY_NAVIGATION_ACTION = 0
internal const val POLICY_NEW_WINDOW_ACTION = 1

/** What the `decide-policy` handler does with one navigation. */
internal sealed interface NavigationVerdict {
    /** Let WebKit load it (`webkit_policy_decision_use`). */
    data object Allow : NavigationVerdict

    /** Cancel it silently (`webkit_policy_decision_ignore`). */
    data object Block : NavigationVerdict

    /** Cancel it and hand [uri] to the owner's link listener (once). */
    data class BlockAndReport(val uri: String) : NavigationVerdict

    /** Not ours (responses, unknown types): return FALSE and let WebKit's default handling run. */
    data object Default : NavigationVerdict
}

/**
 * Pure navigation policy of a WebKitGTK pane.
 *
 * Frame identity: the installed WebKitGTK (2.52) has no source/target frame info on a navigation action, and
 * `get_frame_name` is the `target` attribute, not a main-frame test. The pane therefore fails closed: its web view is
 * created with the default content security policy [FRAME_LOCKDOWN_CSP], so no document of the view can contain a
 * subframe, object or embed. Every navigation action the view sees is thus a main-frame navigation of the bundle page
 * (or a new-window request from it), and the bridge shim, injected into the top frame only, cannot be reached by a
 * foreign document. This replaces the KCEF engine's per-message frame check.
 *
 * - Navigation action to the bundle origin ([isAtBundleBase]: exact host, no port, no userinfo): [NavigationVerdict.Allow].
 * - Navigation action elsewhere: blocked and reported (BibleView opens every link with `window.location.assign`).
 * - New-window action: never allowed (no GTK popup window); reported once, unless the URI is empty.
 * - Anything else (responses): [NavigationVerdict.Default].
 */
internal fun decideNavigation(type: Int, uri: String?): NavigationVerdict = when (type) {
    POLICY_NAVIGATION_ACTION -> when {
        uri.isNullOrEmpty() -> NavigationVerdict.Block
        isAtBundleBase(uri) -> NavigationVerdict.Allow
        else -> NavigationVerdict.BlockAndReport(uri)
    }
    POLICY_NEW_WINDOW_ACTION -> if (uri.isNullOrEmpty()) NavigationVerdict.Block else NavigationVerdict.BlockAndReport(uri)
    else -> NavigationVerdict.Default
}

/**
 * The `default-content-security-policy` of every pane web view (construct-only property, additive with the document's
 * own policy). BibleView-JS uses no frames, objects or embeds; inline script and eval stay untouched.
 */
internal const val FRAME_LOCKDOWN_CSP = "frame-src 'none'; object-src 'none'"

/** `0xAARRGGBB` as the four floats (red, green, blue, alpha) of a `GdkRGBA`. */
internal fun argbToRgba(argb: Int): FloatArray = floatArrayOf(
    ((argb shr 16) and 0xFF) / 255f,
    ((argb shr 8) and 0xFF) / 255f,
    (argb and 0xFF) / 255f,
    ((argb ushr 24) and 0xFF) / 255f,
)
