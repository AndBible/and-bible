import XCTest

/// Reading view VoiceOver and WebView re-parenting (I2 phase 2 R2, Task 8). Real app, seeded KJV: the tests launch with
/// the debug-only `-openRef John.3.16` (see `IosDebugLaunchArgs.kt`) and WITHOUT `-resetData`, so they need the Bible
/// that `AppUITests.testFreshInstallEasyStartReadsJohn3` installed (the run order is alphabetical: AppUITests first;
/// when no Bible is installed the tests skip instead of failing).
///
/// Written on a Linux container without Xcode: NOT compiled and NOT run. The UI labels used to add/close a window
/// and to open a sheet are taken from the strings the app shows ("New window", "Close", ...), which still have to be
/// confirmed on a Mac; every helper that taps by label attaches the element tree when it cannot find its target.
final class ReadingUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    // MARK: helpers (same patterns as AppUITests)

    private func launchReading() throws -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-openRef", "John.3.16"]
        app.launch()
        if element(app, "welcome-screen").waitForExistence(timeout: 3) {
            throw XCTSkip("No installed Bible (Welcome shown): run AppUITests.testFreshInstallEasyStartReadsJohn3 first")
        }
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 60), "reading host not shown")
        XCTAssertTrue(john3Shown(app), "John 3 not rendered in the first window")
        return app
    }

    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    private func attachTree(_ app: XCUIApplication, _ step: String) {
        let a = XCTAttachment(string: app.debugDescription); a.name = "tree-\(step)"; a.lifetime = .keepAlways; add(a)
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let a = XCTAttachment(screenshot: app.screenshot()); a.name = name; a.lifetime = .keepAlways; add(a)
    }

    private func waitUntil(timeout: TimeInterval, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            usleep(500_000)
        }
        return condition()
    }

    /// The chapter-opening 'Nicodemus' phrase label exists somewhere in a web view.
    private func john3Shown(_ app: XCUIApplication, timeout: TimeInterval = 60) -> Bool {
        app.webViews.staticTexts.matching(NSPredicate(format: "label == 'Nicodemus'")).firstMatch
            .waitForExistence(timeout: timeout)
    }

    /// The distinct pane identifiers (`bible-webview-<windowId>`) among the WebView elements. XCUITest reports
    /// several WebView-type elements per WKWebView, so only distinct identifiers count panes.
    private func paneIds(_ app: XCUIApplication) -> [String] {
        let ids = app.webViews.matching(NSPredicate(format: "identifier BEGINSWITH 'bible-webview-'"))
            .allElementsBoundByIndex.map(\.identifier)
        return Array(Set(ids)).sorted()
    }

    /// All 'Nicodemus' labels in the whole app tree (every pane's web view together). The query returns each element
    /// once, so nested WebView-type elements of one WKWebView do not inflate it. Assumption (owed Mac run): a pane that
    /// is in the accessibility tree twice after a re-parent shows its label twice here.
    private func nicodemusInWebViews(_ app: XCUIApplication) -> Int {
        app.webViews.staticTexts.matching(NSPredicate(format: "label == 'Nicodemus'")).count
    }

    /// Same count over `app.staticTexts` (not restricted to web view ancestors), recorded as a cross-check.
    private func nicodemusInApp(_ app: XCUIApplication) -> Int {
        app.staticTexts.matching(NSPredicate(format: "label == 'Nicodemus'")).count
    }

    /// There are `count` distinct panes and exactly `count` 'Nicodemus' labels in the whole tree: one per pane. Fails on
    /// 0 for a pane (count too low), and on a duplicated pane (count too high), unlike a per-pane `firstMatch` lookup.
    /// Both counts are attached; the assertion uses the web view one (robust to the app tree holding other texts).
    private func assertTextOncePerPane(_ app: XCUIApplication, panes count: Int, _ step: String) {
        let ok = waitUntil(timeout: 30) {
            paneIds(app).count == count && nicodemusInWebViews(app) == count
        }
        let summary = "panes=\(paneIds(app)) webViewLabels=\(nicodemusInWebViews(app)) appLabels=\(nicodemusInApp(app))"
        let a = XCTAttachment(string: summary); a.name = "text-once-\(step)"; a.lifetime = .keepAlways; add(a)
        if !ok { attachTree(app, step) }
        XCTAssertTrue(ok, "\(step): expected \(count) panes and \(count) 'Nicodemus' labels (once per pane), got \(summary)")
    }

    /// The tab-rail buttons that stand for windows (labelled with the module, 'KJV' here).
    private func windowTabs(_ app: XCUIApplication) -> [XCUIElement] {
        app.buttons.matching(NSPredicate(format: "label CONTAINS 'KJV'")).allElementsBoundByIndex
    }

    /// Exact label match only (items that open a dialog carry a trailing " …"), so a "Close" elsewhere is not hit.
    private func tapMenuItem(_ app: XCUIApplication, _ labels: [String], _ step: String) {
        for label in labels {
            let item = app.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", label, label + " …")).firstMatch
            if item.waitForExistence(timeout: 5) { item.tap(); return }
        }
        attachTree(app, "\(step)-no-menu-item")
        XCTFail("\(step): none of the menu items \(labels) exists")
    }

    /// Adds a window: the rail's '+' ("New window") while there is one window, else the long-press menu of a tab.
    private func addWindow(_ app: XCUIApplication, from windows: Int) {
        let plus = app.buttons["New window"]
        if windows == 1, plus.waitForExistence(timeout: 10) {
            plus.tap()
        } else if let tab = windowTabs(app).first {
            tab.press(forDuration: 1.0)
            tapMenuItem(app, ["New window"], "add window to \(windows)")
        } else {
            attachTree(app, "add-window-no-control")
            XCTFail("no control to add a window with \(windows) window(s)")
        }
        assertTextOncePerPane(app, panes: windows + 1, "after adding window \(windows + 1)")
    }

    /// Closes the window of the rail tab at `index` through its long-press menu.
    private func closeWindow(_ app: XCUIApplication, tabIndex index: Int, expectingPanes remaining: Int) {
        let tabs = windowTabs(app)
        guard tabs.count > index else {
            attachTree(app, "close-no-tab")
            XCTFail("rail has \(tabs.count) window tabs, wanted index \(index)")
            return
        }
        tabs[index].press(forDuration: 1.0)
        tapMenuItem(app, ["Close"], "close window")
        assertTextOncePerPane(app, panes: remaining, "after closing the middle window")
    }

    // MARK: tests

    /// Split into three windows, close the middle one, add one again: every pane shows the verse text exactly once in
    /// the accessibility tree (a re-parented WKWebView must neither drop out nor appear twice), the closed window's pane
    /// is gone and the reopened window gets a fresh pane id (its own session and web view).
    func testSplitThreeCloseMiddleReopenKeepsTextOnce() throws {
        let app = try launchReading()
        assertTextOncePerPane(app, panes: 1, "single window")
        addWindow(app, from: 1)
        addWindow(app, from: 2)
        let three = paneIds(app)
        XCTAssertEqual(three.count, 3, "three windows expected, got \(three)")
        shot(app, "split-three")

        closeWindow(app, tabIndex: 1, expectingPanes: 2)
        let afterClose = paneIds(app)
        XCTAssertEqual(Set(afterClose).subtracting(three), [], "closing created a pane: \(afterClose)")
        XCTAssertEqual(Set(three).subtracting(afterClose).count, 1, "exactly one pane must be gone: \(three) -> \(afterClose)")

        addWindow(app, from: 2)
        let reopened = paneIds(app)
        XCTAssertEqual(reopened.count, 3, "three panes after reopening, got \(reopened)")
        XCTAssertEqual(Set(reopened).subtracting(afterClose).count, 1, "the reopened window gets its own new pane: \(reopened)")
        shot(app, "split-three-reopened")
    }

    /// Opens a sheet over the reading view and dismisses it: the panes (re-parented by Compose when the sheet host
    /// recomposes) must still expose their text once, and VoiceOver's order (the tree order of the panes) must follow
    /// the visual order (top-to-bottom, then left-to-right).
    func testReparentAfterSheetKeepsVoiceOverOrder() throws {
        let app = try launchReading()
        addWindow(app, from: 1)
        let before = paneIds(app)
        XCTAssertEqual(before.count, 2)

        // A sheet reachable from the reading chrome: check every label with a short wait, then once with a longer one.
        let candidates = ["Search", "Bookmarks", "History"].map { app.buttons[$0] }
        var opener = candidates.first { $0.waitForExistence(timeout: 1) }
        if opener == nil { opener = candidates.first { $0.waitForExistence(timeout: 10) } }
        guard let opener else {
            attachTree(app, "no-sheet-opener")
            XCTFail("no reading control that opens a sheet (Search/Bookmarks/History)")
            return
        }
        opener.tap()
        // The sheet is up when the reading chrome's opener is covered or a dismiss control appears.
        let sheetDismiss = app.buttons.matching(NSPredicate(format: "label IN {'Close', 'Cancel', 'Back'}")).firstMatch
        let sheetUp = sheetDismiss.waitForExistence(timeout: 10)
        shot(app, "sheet-open")
        if sheetUp {
            sheetDismiss.tap()
        } else {
            // No dismiss button: drag the sheet's grabber region (upper part of the sheet), never a swipe inside the web view.
            attachTree(app, "sheet-no-dismiss-button")
            let handle = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.35))
            handle.press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.98)))
        }

        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 15), "reading host not back after the sheet")
        XCTAssertEqual(paneIds(app), before, "panes changed across the sheet (a pane was recreated)")
        assertTextOncePerPane(app, panes: 2, "after the sheet")

        // The tree order of the panes follows the visual order.
        let frames = app.webViews.matching(NSPredicate(format: "identifier BEGINSWITH 'bible-webview-'"))
            .allElementsBoundByIndex.map { (id: $0.identifier, frame: $0.frame) }
        var seen = Set<String>(), ordered: [(id: String, frame: CGRect)] = []
        for f in frames where seen.insert(f.id).inserted { ordered.append(f) }
        for (a, b) in zip(ordered, ordered.dropFirst()) {
            let inOrder = a.frame.minY < b.frame.minY - 1 || (abs(a.frame.minY - b.frame.minY) <= 1 && a.frame.minX <= b.frame.minX)
            if !inOrder { attachTree(app, "voiceover-order") }
            XCTAssertTrue(inOrder, "VoiceOver order differs from the visual order: \(a.id) \(a.frame) before \(b.id) \(b.frame)")
        }
    }

    /// iOS 17+ accessibility audit of the reading view. Findings are recorded, not failed (like the PoC test); an audit
    /// that does not finish in time is a runner problem and skips.
    func testAccessibilityAuditReading() throws {
        let app = try launchReading()
        addWindow(app, from: 1)
        if #available(iOS 17.0, *) {
            do {
                try app.performAccessibilityAudit { issue in
                    let a = XCTAttachment(string: issue.debugDescription); a.name = "a11y-issue"; a.lifetime = .keepAlways
                    self.add(a)
                    return true   // record, do not fail
                }
            } catch let error as NSError where error.domain == "com.apple.xcode.xctest.accessibilityAudit" && error.code == -56 {
                throw XCTSkip("Accessibility audit did not complete in time on this runner")
            }
        } else {
            throw XCTSkip("performAccessibilityAudit needs iOS 17")
        }
    }
    // MARK: Task 9 helpers (labels from the Kotlin sources: see the notes on each test)

    /// Any element type with exactly this label. Compose icon buttons are merged `Row`s whose label is the icon's
    /// contentDescription; which XCUIElement type they surface as is not known, so the type is not restricted.
    private func byLabel(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

    /// A fresh process on the reading view (the previous one is terminated first), for steps that leave the toolbar in
    /// a state with no way back (the search row, the speak bar).
    private func relaunchReading(_ app: XCUIApplication) throws -> XCUIApplication {
        app.terminate()
        return try launchReading()
    }

    /// The quick sheet container (`ReadingQuickSheetTag`, a `testTag` on the box inside the sheet).
    private func quickSheet(_ app: XCUIApplication) -> XCUIElement { element(app, "reading-quick-sheet") }

    /// Closes the open quick sheet with the sheet header's close button (`settingsEditorClose` = "Close") and waits
    /// for the sheet to leave the tree. Returns false (tree attached) when it stays.
    @discardableResult
    private func closeQuickSheet(_ app: XCUIApplication, _ step: String) -> Bool {
        let close = byLabel(app, "Close")
        guard close.waitForExistence(timeout: 10) else {
            attachTree(app, "\(step)-no-close-button")
            XCTFail("\(step): the sheet has no Close button")
            return false
        }
        close.tap()
        let gone = waitUntil(timeout: 10) { !quickSheet(app).exists }
        if !gone { attachTree(app, "\(step)-sheet-stuck") }
        XCTAssertTrue(gone, "\(step): the sheet is still shown after Close")
        return gone
    }

    /// The pane identifiers whose web view is actually on screen (hittable), as opposed to merely in the tree.
    private func visiblePaneIds(_ app: XCUIApplication) -> [String] {
        let ids = app.webViews.matching(NSPredicate(format: "identifier BEGINSWITH 'bible-webview-'"))
            .allElementsBoundByIndex.filter { $0.isHittable }.map(\.identifier)
        return Array(Set(ids)).sorted()
    }

    /// The toolbar's page title ("John 3:16" style): a static text starting with the book name in the top strip of
    /// the screen, so the chapter heading inside a web view is not hit.
    private func toolbarTitle(_ app: XCUIApplication) -> XCUIElement? {
        app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'John'")).allElementsBoundByIndex
            .first { $0.frame.minY < 160 && $0.frame.height > 0 }
    }

    // MARK: Task 9 tests

    /// Toolbar actions and where they lead. Labels are the toolbar's contentDescriptions (strings.xml): search is
    /// "Find" (NOT "Search"), speak "Speak", the workspace button "Workspaces", the overflow "More options".
    /// Each action runs in a fresh process and records its own failure (`continueAfterFailure` on), so one dead
    /// toolbar button does not hide the others.
    ///
    /// At the time of writing `IosReadingHost.callbacks()` still routes onSpeak, onWorkspace, onOverflow (and
    /// onBible/onCommentary/onHome) to `IosPending.log`, so those steps are EXPECTED red until that wiring lands:
    /// that is the defect they name (a toolbar button that does nothing).
    func testToolbarActionsOpenTheirDestinations() throws {
        continueAfterFailure = true
        var app = try launchReading()

        XCTContext.runActivity(named: "search opens the search field") { _ in
            let find = byLabel(app, "Find")
            guard find.waitForExistence(timeout: 10) else {
                attachTree(app, "toolbar-no-find"); XCTFail("no 'Find' toolbar button"); return
            }
            find.tap()
            let field = element(app, "reading-search-field")   // SEARCH_FIELD_TAG
            let shown = field.waitForExistence(timeout: 15)
            if !shown { attachTree(app, "search-not-shown") }
            XCTAssertTrue(shown, "tapping Find did not open the search field")
            shot(app, "toolbar-search")
        }

        XCTContext.runActivity(named: "speak shows the transport bar") { _ in
            app = (try? relaunchReading(app)) ?? app
            let speak = byLabel(app, "Speak")
            guard speak.waitForExistence(timeout: 10) else {
                attachTree(app, "toolbar-no-speak"); XCTFail("no 'Speak' toolbar button"); return
            }
            speak.tap()
            // The transport bar has Previous / Next (speak_previous, speak_next) next to play-pause.
            let next = byLabel(app, "Next")
            let shown = next.waitForExistence(timeout: 15)
            if !shown { attachTree(app, "speak-bar-not-shown") }
            XCTAssertTrue(shown, "tapping Speak did not show the speak transport bar (no 'Next' control)")
            shot(app, "toolbar-speak")
        }

        XCTContext.runActivity(named: "workspace opens the workspaces sheet") { _ in
            app = (try? relaunchReading(app)) ?? app
            let ws = byLabel(app, "Workspaces")
            guard ws.waitForExistence(timeout: 10) else {
                attachTree(app, "toolbar-no-workspaces"); XCTFail("no 'Workspaces' toolbar button"); return
            }
            ws.tap()
            let shown = quickSheet(app).waitForExistence(timeout: 15)
            if !shown { attachTree(app, "workspace-sheet-not-shown") }
            XCTAssertTrue(shown, "tapping Workspaces did not open the workspaces quick sheet")
            if shown { closeQuickSheet(app, "workspaces") }
        }

        XCTContext.runActivity(named: "overflow opens a menu") { _ in
            app = (try? relaunchReading(app)) ?? app
            let more = byLabel(app, "More options")
            guard more.waitForExistence(timeout: 10) else {
                attachTree(app, "toolbar-no-overflow"); XCTFail("no 'More options' toolbar button"); return
            }
            let before = app.descendants(matching: .any).count
            more.tap()
            // The menu's item labels are built by the host and not known here: a menu is shown when the tree grows.
            let shown = waitUntil(timeout: 10) { app.descendants(matching: .any).count > before + 1 }
            if !shown { attachTree(app, "overflow-not-shown") }
            XCTAssertTrue(shown, "tapping More options did not show a menu (element count stayed at \(before))")
            shot(app, "toolbar-overflow")
        }
    }

    /// Every quick sheet that has an opener on the reading chrome opens, shows the sheet container and closes again,
    /// leaving the reading host and the pane untouched (the sheet is its own window, so no pane may be recreated).
    /// Openers: title tap (key chooser, wired at HEAD), Workspaces / Bible / Commentary toolbar buttons (pending at HEAD,
    /// like the toolbar test). History and the document sheet have no reading-chrome opener on iOS yet (drawer and title
    /// long-press are pending): they are not covered here, see the report.
    func testEachQuickSheetOpensAndCloses() throws {
        continueAfterFailure = true
        var app = try launchReading()

        let openers: [(name: String, open: (XCUIApplication) -> Bool)] = [
            ("key chooser (title tap)", { app in
                guard let title = self.toolbarTitle(app) else { return false }
                title.tap(); return true
            }),
            ("workspaces", { app in
                let b = self.byLabel(app, "Workspaces"); guard b.waitForExistence(timeout: 10) else { return false }
                b.tap(); return true
            }),
            ("bible documents", { app in
                let b = self.byLabel(app, "Bible"); guard b.waitForExistence(timeout: 10) else { return false }
                b.tap(); return true
            }),
            ("commentary documents", { app in
                let b = self.byLabel(app, "Commentary"); guard b.waitForExistence(timeout: 10) else { return false }
                b.tap(); return true
            }),
        ]
        for opener in openers {
            XCTContext.runActivity(named: opener.name) { _ in
                app = (try? relaunchReading(app)) ?? app
                let panesBefore = paneIds(app)
                guard opener.open(app) else {
                    attachTree(app, "sheet-\(opener.name)-no-opener")
                    XCTFail("\(opener.name): the opener control does not exist")
                    return
                }
                let shown = quickSheet(app).waitForExistence(timeout: 15)
                shot(app, "sheet-\(opener.name)")
                if !shown {
                    attachTree(app, "sheet-\(opener.name)-not-shown")
                    XCTFail("\(opener.name): no quick sheet after tapping the opener")
                    return
                }
                guard closeQuickSheet(app, opener.name) else { return }
                XCTAssertTrue(element(app, "reading-host").exists, "\(opener.name): reading host gone after the sheet")
                XCTAssertEqual(paneIds(app), panesBefore, "\(opener.name): a pane was recreated by opening the sheet")
                XCTAssertTrue(john3Shown(app, timeout: 15), "\(opener.name): chapter text missing after the sheet")
            }
        }
    }

    /// Split, maximise, minimise: the window pane menu rows are "Maximise" and "Minimise" (strings windowMaximise /
    /// windowMinimise; "Minimise" is disabled when the window is not minimisable) and the rail's leading control while a
    /// window is maximised is "Restore all windows" (window_unmaximise). Pane visibility is measured with isHittable
    /// on the `bible-webview-<id>` elements, not tree presence.
    func testSplitMaximiseMinimiseWindows() throws {
        let app = try launchReading()
        addWindow(app, from: 1)
        XCTAssertEqual(visiblePaneIds(app).count, 2, "two panes must both be visible after the split: \(visiblePaneIds(app))")

        // Maximise the first window.
        guard let tab = windowTabs(app).first else {
            attachTree(app, "maximise-no-tab"); XCTFail("no window tab to open the pane menu from"); return
        }
        tab.press(forDuration: 1.0)
        tapMenuItem(app, ["Maximise"], "maximise")
        let restore = byLabel(app, "Restore all windows")
        let maximised = restore.waitForExistence(timeout: 15)
        if !maximised { attachTree(app, "maximise-no-restore-control") }
        XCTAssertTrue(maximised, "maximising did not show the 'Restore all windows' rail control")
        XCTAssertTrue(waitUntil(timeout: 15) { visiblePaneIds(app).count == 1 },
                      "exactly one pane must be visible while maximised, got \(visiblePaneIds(app))")
        shot(app, "maximised")

        // Restore: both panes back, and the text still appears once per pane.
        if maximised { restore.tap() }
        XCTAssertTrue(waitUntil(timeout: 15) { visiblePaneIds(app).count == 2 },
                      "restoring did not bring both panes back, got \(visiblePaneIds(app))")
        assertTextOncePerPane(app, panes: 2, "after restore")

        // Minimise one window: its pane leaves the screen, the other stays.
        guard let tab2 = windowTabs(app).last else {
            attachTree(app, "minimise-no-tab"); XCTFail("no window tab for the minimise step"); return
        }
        tab2.press(forDuration: 1.0)
        tapMenuItem(app, ["Minimise"], "minimise")
        XCTAssertTrue(waitUntil(timeout: 15) { visiblePaneIds(app).count == 1 },
                      "minimising one of two windows must leave one visible pane, got \(visiblePaneIds(app))")
        shot(app, "minimised")

        // Bring it back through its rail tab (a minimised window's tab restores it).
        if let minimisedTab = windowTabs(app).last {
            minimisedTab.tap()
            if !waitUntil(timeout: 15, { visiblePaneIds(app).count == 2 }) { attachTree(app, "minimise-restore-failed") }
            XCTAssertEqual(visiblePaneIds(app).count, 2, "tapping the minimised window's tab did not restore its pane")
        }
    }

    /// iPad only: rotate with a quick sheet open and back. The sheet must reflow (stay inside the window, keep its
    /// close control reachable) and must not stay stuck at a half-height or stale size: after rotating back to portrait
    /// its frame and the close button's frame equal those from before the rotation (same content, same orientation).
    /// The reading host and panes must survive, and the sheet must still close.
    func testIPadRotationWithSheetOpenReflows() throws {
        guard isPad else { throw XCTSkip("iPad rotation test (sheets are full-width on iPhone)") }
        continueAfterFailure = true
        let app = try launchReading()
        let device = XCUIDevice.shared
        addTeardownBlock { device.orientation = .portrait }
        device.orientation = .portrait
        XCTAssertTrue(waitUntil(timeout: 10) { app.windows.firstMatch.frame.height > app.windows.firstMatch.frame.width },
                      "could not start in portrait")

        // Open a sheet that is wired at HEAD (title tap), else the workspaces one.
        var opened = false
        if let title = toolbarTitle(app) { title.tap(); opened = quickSheet(app).waitForExistence(timeout: 15) }
        if !opened {
            let ws = byLabel(app, "Workspaces")
            if ws.waitForExistence(timeout: 5) { ws.tap(); opened = quickSheet(app).waitForExistence(timeout: 15) }
        }
        guard opened else {
            attachTree(app, "rotation-no-sheet"); XCTFail("no quick sheet could be opened for the rotation test"); return
        }
        let close = byLabel(app, "Close")
        XCTAssertTrue(close.waitForExistence(timeout: 10), "sheet has no Close button")
        let portraitSheet = quickSheet(app).frame
        let portraitClose = close.frame
        shot(app, "rotation-portrait-before")

        // Landscape.
        device.orientation = .landscapeLeft
        XCTAssertTrue(waitUntil(timeout: 15) { app.windows.firstMatch.frame.width > app.windows.firstMatch.frame.height },
                      "window did not become landscape")
        usleep(1_500_000)   // let the sheet re-measure
        let win = app.windows.firstMatch.frame
        let landscapeSheet = quickSheet(app).frame
        shot(app, "rotation-landscape")
        XCTAssertTrue(quickSheet(app).exists, "the sheet vanished on rotation (it must reflow, not close)")
        XCTAssertTrue(win.insetBy(dx: -1, dy: -1).contains(landscapeSheet),
                      "sheet \(landscapeSheet) is not inside the landscape window \(win)")
        XCTAssertGreaterThan(landscapeSheet.height, 100, "sheet collapsed in landscape: \(landscapeSheet)")
        XCTAssertTrue(close.exists && close.isHittable, "Close not reachable in landscape")
        XCTAssertTrue(win.insetBy(dx: -1, dy: -1).contains(close.frame), "Close \(close.frame) outside window \(win)")

        // Back to portrait: not stuck at the landscape (or a half) height.
        device.orientation = .portrait
        XCTAssertTrue(waitUntil(timeout: 15) { app.windows.firstMatch.frame.height > app.windows.firstMatch.frame.width },
                      "window did not return to portrait")
        usleep(1_500_000)
        let backSheet = quickSheet(app).frame
        let backClose = close.frame
        shot(app, "rotation-portrait-after")
        XCTAssertEqual(backSheet.height, portraitSheet.height, accuracy: 4,
                       "sheet height after rotating back (\(backSheet)) differs from before (\(portraitSheet)): stuck sheet size")
        XCTAssertEqual(backSheet.width, portraitSheet.width, accuracy: 4, "sheet width after rotating back differs")
        XCTAssertEqual(backClose.minY, portraitClose.minY, accuracy: 4, "Close moved: sheet did not return to its place")
        XCTAssertTrue(app.windows.firstMatch.frame.insetBy(dx: -1, dy: -1).contains(backSheet), "sheet outside window in portrait")

        // The sheet still closes, and the pane is intact and inside the window.
        if closeQuickSheet(app, "rotation") {
            XCTAssertTrue(element(app, "reading-host").exists, "reading host missing after rotation")
            XCTAssertTrue(john3Shown(app, timeout: 15), "chapter text missing after rotation")
            let pane = app.webViews.matching(NSPredicate(format: "identifier BEGINSWITH 'bible-webview-'")).firstMatch
            XCTAssertTrue(app.windows.firstMatch.frame.insetBy(dx: -1, dy: -1).contains(pane.frame),
                          "pane \(pane.frame) outside the window after rotation")
        }
    }
}
