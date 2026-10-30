package net.bible.desktop.webview2

/**
 * Vtable slots and IIDs of the WebView2 COM interfaces this module calls, taken from `WebView2.h` of the
 * Microsoft.Web.WebView2 NuGet package 1.0.4191.47 (slot = position in the C vtable struct, IUnknown's three included).
 * Interfaces only grow at the end, so a slot never changes for an interface ID.
 */
object Wv2 {
    object Iids {
        val Environment3 = Iid("80a22ae3-be7c-4ce2-afe1-5a50056cdeeb")
        val Controller2 = Iid("c979903e-d4ca-4228-92eb-47ee3fa96eab")
        val Controller3 = Iid("f9614724-5d2b-41dc-aef7-73d62b51543b")
        val Settings3 = Iid("fdb5ab74-af33-4854-84f0-0a631deb5eba")
        val WebView2_11 = Iid("0be78e56-c193-4051-b943-23b460c08bdb")
        val CreateEnvironmentCompleted = Iid("4e8a3389-c9d8-4bd2-b6b5-124fee6cc14d")
        val CreateCompositionControllerCompleted = Iid("02fab84b-1428-4fb7-ad45-1b2e64736184")
        val ExecuteScriptCompleted = Iid("49511172-cc67-4bca-9923-137112f4c4cc")
        val WebMessageReceived = Iid("57213f19-00e6-49fa-8e07-898ea01ecbd2")
        val NavigationStarting = Iid("9adbe429-f36d-432b-9ddc-f8881fbd76e3")
        val NavigationCompleted = Iid("d33a35bf-1c49-4f98-93ab-006e0533fe1c")
        val NewWindowRequested = Iid("d4c185fe-c81c-4989-97af-2d3fa7ab5651")
        val WebResourceRequested = Iid("ab00b74c-15f1-4646-80e8-e76341d25d71")
        val ProcessFailed = Iid("79e0aea4-990b-42d9-aa1d-0fcc2e5bc7f1")
        val CursorChanged = Iid("9da43ccc-26e1-4dad-b56c-d8961c94c571")
        val ContextMenuRequested = Iid("04d3fe1d-ab87-42fb-a898-da241d35b63c")
    }

    /** ICoreWebView2Environment(3). */
    object Env {
        const val CreateWebResourceResponse = 4
        const val get_BrowserVersionString = 5
        const val CreateCoreWebView2CompositionController = 9
    }

    /** ICoreWebView2Controller(2, 3). */
    object Controller {
        const val put_IsVisible = 4
        const val put_Bounds = 6
        const val MoveFocus = 12
        const val put_ParentWindow = 22
        const val NotifyParentWindowPositionChanged = 23
        const val Close = 24
        const val get_CoreWebView2 = 25
        const val put_DefaultBackgroundColor = 27
        const val put_RasterizationScale = 29
        const val put_ShouldDetectMonitorScaleChanges = 31
    }

    /** ICoreWebView2CompositionController. */
    object Composition {
        const val put_RootVisualTarget = 4
        const val SendMouseInput = 5
        const val get_SystemCursorId = 8
        const val add_CursorChanged = 9
    }

    /** ICoreWebView2 (and _11 for the context menu). */
    object WebView {
        const val get_Settings = 3
        const val get_Source = 4
        const val Navigate = 5
        const val add_NavigationStarting = 7
        const val add_NavigationCompleted = 15
        const val add_ProcessFailed = 25
        const val AddScriptToExecuteOnDocumentCreated = 27
        const val ExecuteScript = 29
        const val add_WebMessageReceived = 34
        const val get_BrowserProcessId = 37
        const val add_NewWindowRequested = 44
        const val add_WebResourceRequested = 55
        const val AddWebResourceRequestedFilter = 57
        const val add_ContextMenuRequested = 100
    }

    /** ICoreWebView2Settings. */
    object Settings {
        const val put_AreDefaultScriptDialogsEnabled = 8
        const val put_IsStatusBarEnabled = 10
        const val put_AreDevToolsEnabled = 12
        const val put_AreDefaultContextMenusEnabled = 14
        const val put_AreHostObjectsAllowed = 16
        const val put_IsZoomControlEnabled = 18
        const val put_IsBuiltInErrorPageEnabled = 20

        /** ICoreWebView2Settings3. */
        const val put_AreBrowserAcceleratorKeysEnabled = 24
    }

    object WebMessageArgs {
        const val get_Source = 3
        const val TryGetWebMessageAsString = 5
    }

    object NavigationStartingArgs {
        const val get_Uri = 3
        const val put_Cancel = 8
    }

    object NewWindowArgs {
        const val get_Uri = 3
        const val put_Handled = 6
    }

    object ResourceArgs {
        const val get_Request = 3
        const val put_Response = 5
        const val GetDeferral = 6
    }

    /** ICoreWebView2Deferral. */
    object Deferral {
        const val Complete = 3
    }

    object Request {
        const val get_Uri = 3
    }

    object ProcessFailedArgs {
        const val get_ProcessFailedKind = 3
    }

    object ContextMenuArgs {
        const val put_Handled = 8
    }

    /** COREWEBVIEW2_MOUSE_EVENT_KIND values. */
    object Mouse {
        const val MOVE = 512
        const val LEFT_DOWN = 513
        const val LEFT_UP = 514
        const val LEFT_DOUBLE_CLICK = 515
        const val RIGHT_DOWN = 516
        const val RIGHT_UP = 517
        const val MIDDLE_DOWN = 519
        const val MIDDLE_UP = 520
        const val WHEEL = 522
        const val HORIZONTAL_WHEEL = 526
        const val LEAVE = 675

        /** COREWEBVIEW2_MOUSE_EVENT_VIRTUAL_KEYS bits. */
        const val VK_LEFT = 0x1
        const val VK_RIGHT = 0x2
        const val VK_SHIFT = 0x4
        const val VK_CONTROL = 0x8
        const val VK_MIDDLE = 0x10
    }

    /** COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL. */
    const val RESOURCE_CONTEXT_ALL = 0

    /** COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC. */
    const val MOVE_FOCUS_PROGRAMMATIC = 0

    /** COREWEBVIEW2_PROCESS_FAILED_KIND values that end the page (renderer exited or unresponsive, browser exited). */
    const val PROCESS_FAILED_BROWSER_EXITED = 0
    const val PROCESS_FAILED_RENDER_EXITED = 1
    const val PROCESS_FAILED_RENDER_UNRESPONSIVE = 2

    /** IDCompositionDevice / IDCompositionTarget (dcomp.h). */
    object Dcomp {
        const val Commit = 3
        const val CreateTargetForHwnd = 6
        const val CreateVisual = 7
        const val Target_SetRoot = 3
    }
}
