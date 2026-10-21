import XCTest

/// Reading view VoiceOver and WebView re-parenting (I2 phase 2 R2, Task 8). Real app on KJV: the tests launch with the
/// debug-only `-openRef John.3.16` on `-openDocument KJV` (see `IosDebugLaunchArgs.kt`), `-resetDatabases` (fresh
/// one-window workspace, installed modules kept) and `-standardTestFixture` (English UI, no disguise), so no test
/// inherits the windows, document or language another one left. When KJV is not installed (a `-resetData` test ran
/// before: Welcome, or reading without John 3), the launch provisions it through the real Easy start, as `AppUITests`
/// does; the suite does not depend on the run order. Labels were confirmed against Mac element trees.
final class ReadingUITests: RealAppUITestCase {
    /// 'Nicodemus' is a phrase label three times in KJV John 3 (verses 1, 4 and 9), in every pane showing the chapter.
    private let nicodemusPerPane = 3

    // MARK: helpers (launch, element, shot, attachTree, waitUntil, john3Shown, paneIds: RealAppUITestCase)

    private func launchReading(extraArguments: [String] = []) throws -> XCUIApplication {
        // KJV explicitly: a fresh workspace opens the first installed Bible, which other suites' fixtures can shadow.
        // `extraArguments` (e.g. the content size category of the Dynamic Type tests) go after the fixed ones.
        let arguments = ["-standardTestFixture", "-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16"] + extraArguments
        var app = launch(arguments)
        let welcome = element(app, "welcome-screen"), host = element(app, "reading-host")
        XCTAssertTrue(waitUntil(timeout: 60) { welcome.exists || host.exists }, "neither Welcome nor reading shown")
        if welcome.exists || !john3Shown(app, timeout: 30) {
            app.terminate()
            let fresh = freshReading()
            selectKJV(fresh)
            sendToBackground(fresh)
            fresh.terminate()
            app = launch(arguments)
        }
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 60), "reading host not shown")
        XCTAssertTrue(john3Shown(app), "John 3 not rendered in the first window")
        return app
    }

    /// All 'Nicodemus' labels in the whole app tree (every pane's web view together). The query returns each element
    /// once, so nested WebView-type elements of one WKWebView do not inflate it: a Mac run counted 3 per pane.
    private func nicodemusInWebViews(_ app: XCUIApplication) -> Int {
        app.webViews.staticTexts.matching(NSPredicate(format: "label == 'Nicodemus'")).count
    }

    /// Same count over `app.staticTexts` (not restricted to web view ancestors), recorded as a cross-check.
    private func nicodemusInApp(_ app: XCUIApplication) -> Int {
        app.staticTexts.matching(NSPredicate(format: "label == 'Nicodemus'")).count
    }

    /// There are `count` distinct panes and exactly `count` x [nicodemusPerPane] 'Nicodemus' labels in the whole tree:
    /// the chapter once per pane. Fails on a pane without text (too low) and on a duplicated pane (too high), unlike a
    /// per-pane `firstMatch` lookup. Both counts are attached; the assertion uses the web view one.
    private func assertTextOncePerPane(_ app: XCUIApplication, panes count: Int, _ step: String) {
        let expected = count * nicodemusPerPane
        let ok = waitUntil(timeout: 30) {
            paneIds(app).count == count && nicodemusInWebViews(app) == expected
        }
        let summary = "panes=\(paneIds(app)) webViewLabels=\(nicodemusInWebViews(app)) appLabels=\(nicodemusInApp(app))"
        let a = XCTAttachment(string: summary); a.name = "text-once-\(step)"; a.lifetime = .keepAlways; add(a)
        if !ok { attachTree(app, step) }
        XCTAssertTrue(ok, "\(step): expected \(count) panes and \(expected) 'Nicodemus' labels (the chapter once per pane), got \(summary)")
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

        // A sheet reachable on every device: the toolbar title opens the key chooser quick sheet (the Workspaces and
        // Find buttons only fit the toolbar on wide screens), whose header has a "Close" button.
        guard let title = toolbarTitle(app) else {
            attachTree(app, "no-sheet-opener")
            XCTFail("no toolbar title to open the key chooser sheet from")
            return
        }
        title.tap()
        let sheetUp = quickSheetShown(app, timeout: 15)
        shot(app, "sheet-open")
        if !sheetUp { attachTree(app, "sheet-not-open") }
        XCTAssertTrue(sheetUp, "tapping the title did not open the key chooser sheet")
        closeQuickSheet(app, "key chooser")

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

    /// The toolbar's home button ("Menu") opens the drawer; its History row opens the history quick sheet, whose "Close"
    /// button returns to the reading view with the panes unchanged. Labels from strings.xml ("history"); the drawer rows
    /// are Compose navigation items, found as any element type (the tree is attached on failure).
    func testDrawerOpensAndItsHistoryRowOpensTheHistorySheet() throws {
        let app = try launchReading()
        let before = paneIds(app)
        let menu = app.buttons["Menu"]
        XCTAssertTrue(menu.waitForExistence(timeout: 15), "home (Menu) button not found")
        menu.tap()
        let history = app.descendants(matching: .any).matching(NSPredicate(format: "label == 'History'")).firstMatch
        guard history.waitForExistence(timeout: 10) else {
            attachTree(app, "drawer-no-history-row")
            XCTFail("the drawer did not show a History row")
            return
        }
        history.tap()
        let close = app.buttons["Close"]
        XCTAssertTrue(close.waitForExistence(timeout: 10), "the history sheet did not open (no Close button)")
        shot(app, "drawer-history-sheet")
        close.tap()
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 15), "reading host not back after the history sheet")
        XCTAssertEqual(paneIds(app), before, "panes changed across the drawer and the sheet")
    }

    /// iOS 17+ accessibility audit of the reading surfaces (toolbar and window buttons, overflow, drawer, pane menu, a
    /// quick sheet, the speak bar and search-in-place; the selection menu is UIKit-owned and the full-screen overlay has
    /// no stable opener yet, both are not audited). FAILS on any finding: every finding of every surface is collected
    /// first, so one run lists them all, and the failure message (also attached as `a11y-findings`) gives surface,
    /// audit type, element type / label / identifier / frame and the audit's own description. An audit that does not
    /// finish in time is a runner problem and skips.
    func testAccessibilityAuditReading() throws {
        guard #available(iOS 17.0, *) else { throw XCTSkip("performAccessibilityAudit needs iOS 17") }
        try auditReadingSurfaces()
    }

    @available(iOS 17.0, *)
    private func auditReadingSurfaces() throws {
        continueAfterFailure = true
        var findings: [String] = []
        let device = XCUIDevice.shared
        addTeardownBlock { device.orientation = .portrait }

        // Surface name, whether it needs landscape on an iPhone (the toolbar fits Find and Speak only there), opener.
        let surfaces: [(name: String, landscape: Bool, open: (XCUIApplication) -> Bool)] = [
            ("reading with two panes (toolbar, window buttons)", false, { app in
                self.addWindow(app, from: 1); return true
            }),
            ("overflow menu", false, { app in
                let more = self.byLabel(app, "More options")
                guard more.waitForExistence(timeout: 10) else { return false }
                more.tap()
                return app.buttons["Night mode"].waitForExistence(timeout: 10)
            }),
            ("drawer", false, { app in
                let menu = app.buttons["Menu"]
                guard menu.waitForExistence(timeout: 10) else { return false }
                menu.tap()
                return self.byLabel(app, "History").waitForExistence(timeout: 10)
            }),
            ("pane menu", false, { app in
                self.addWindow(app, from: 1)
                guard let tab = self.windowTabs(app).first else { return false }
                tab.press(forDuration: 1.0)
                return app.buttons.matching(NSPredicate(format: "label == 'Maximise' OR label == 'Maximise …'"))
                    .firstMatch.waitForExistence(timeout: 10)
            }),
            ("key chooser quick sheet", false, { app in
                guard let title = self.toolbarTitle(app) else { return false }
                title.tap()
                return self.quickSheetShown(app, timeout: 15)
            }),
            ("speak bar", true, { app in
                let speak = self.byLabel(app, "Speak")
                guard speak.waitForExistence(timeout: 10) else { return false }
                speak.tap()
                return self.byLabel(app, "Next").waitForExistence(timeout: 15)
            }),
            ("search in place", true, { app in
                let find = self.byLabel(app, "Find")
                guard find.waitForExistence(timeout: 10) else { return false }
                find.tap()
                return self.element(app, "reading-search-field").waitForExistence(timeout: 15)
            }),
        ]

        var app = try launchReading()
        for (index, surface) in surfaces.enumerated() {
            if index > 0 {
                if !isPad { device.orientation = surface.landscape ? .landscapeLeft : .portrait }
                app = try relaunchReading(app)
            }
            guard surface.open(app) else {
                attachTree(app, "audit-\(surface.name)-not-reachable")
                XCTFail("audit: surface '\(surface.name)' could not be opened, so it was not audited")
                continue
            }
            shot(app, "audit-\(surface.name)")
            try audit(app, surface: surface.name, into: &findings)
        }

        let note = XCTAttachment(string: "Not audited: selection menu (UIKit-owned), full-screen overlay (no stable opener)")
        note.name = "a11y-not-audited"; note.lifetime = .keepAlways; add(note)
        let report = "\(findings.count) accessibility finding(s):\n" + findings.joined(separator: "\n")
        let attachment = XCTAttachment(string: report); attachment.name = "a11y-findings"; attachment.lifetime = .keepAlways
        add(attachment)
        XCTAssertTrue(findings.isEmpty, report)
    }

    /// Audits what is on screen and appends one line per finding to `findings`. The handler returns true for every issue
    /// so the audit itself does not stop at the first one; the test fails on the collected list afterwards.
    ///
    /// Issue filters: NONE yet. A finding that cannot be fixed in R3 is listed in `knownLimitations` below, each entry
    /// with the reason, and matches narrowly (audit type plus a label or identifier fragment), never a whole category.
    @available(iOS 17.0, *)
    private func audit(_ app: XCUIApplication, surface: String, into findings: inout [String]) throws {
        // (reason, matcher). Add an entry only with the controller's decision; keep it as narrow as possible.
        let knownLimitations: [(reason: String, matches: (XCUIAccessibilityAuditIssue) -> Bool)] = []
        var found: [String] = []
        do {
            try app.performAccessibilityAudit { issue in
                if let limitation = knownLimitations.first(where: { $0.matches(issue) }) {
                    let a = XCTAttachment(string: "[\(surface)] filtered (\(limitation.reason)): \(issue.compactDescription)")
                    a.name = "a11y-filtered"; a.lifetime = .keepAlways; self.add(a)
                    return true
                }
                let element = issue.element.map {
                    "elementType=\($0.elementType.rawValue) label='\($0.label)' identifier='\($0.identifier)' frame=\($0.frame)"
                } ?? "element=none"
                found.append("[\(surface)] auditType=\(issue.auditType.rawValue) \(element) :: \(issue.compactDescription) | \(issue.detailedDescription)")
                return true   // collected, reported and failed after all surfaces
            }
        } catch let error as NSError where error.domain == "com.apple.xcode.xctest.accessibilityAudit" && error.code == -56 {
            throw XCTSkip("Accessibility audit did not complete in time on this runner")
        }
        findings.append(contentsOf: found)
    }

    // MARK: Task 9 helpers (labels from the Kotlin sources: see the notes on each test)

    /// Any element type with exactly this label. Compose icon buttons are merged `Row`s whose label is the icon's
    /// contentDescription; which XCUIElement type they surface as is not known, so the type is not restricted.
    private func byLabel(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }


    /// A fresh process on the reading view (the previous one is terminated first), for steps that leave the toolbar in
    /// a state with no way back (the search row, the speak bar).
    private func relaunchReading(_ app: XCUIApplication, extraArguments: [String] = []) throws -> XCUIApplication {
        app.terminate()
        return try launchReading(extraArguments: extraArguments)
    }

    /// The open quick sheet's surface (`AbQuickSheetTag`). Not `ReadingQuickSheetTag`: that tags a zero-size box in the
    /// main window while `ModalBottomSheet` draws in its own window, so XCUITest never sees it.
    private func quickSheet(_ app: XCUIApplication) -> XCUIElement { element(app, "ab-quick-sheet") }

    private func quickSheetShown(_ app: XCUIApplication, timeout: TimeInterval) -> Bool {
        quickSheet(app).waitForExistence(timeout: timeout)
    }

    /// Closes the open quick sheet with the sheet header's close button (`settingsEditorClose` = "Close") and waits
    /// for the sheet to leave the tree. Returns false (tree attached) when it stays.
    @discardableResult
    private func closeQuickSheet(_ app: XCUIApplication, _ step: String) -> Bool {
        let close = app.buttons["Close"]
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
    /// The toolbar shows as many quick buttons as fit half its width (`fitToolbarButtons`, Android's budget): an iPhone
    /// in portrait fits only Bible, Commentary and Strong's, so the iPhone runs this test in landscape, where all fit.
    func testToolbarActionsOpenTheirDestinations() throws {
        continueAfterFailure = true
        if !isPad {
            let device = XCUIDevice.shared
            addTeardownBlock { device.orientation = .portrait }
            device.orientation = .landscapeLeft
        }
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
            more.tap()
            // The rows always present in `ReadingOverflowController.build()`. Not an element-count check: the modal
            // popup hides the reading view from the tree, so the count drops when the menu opens.
            let shown = ["Night mode", "All text options …"].allSatisfy { app.buttons[$0].waitForExistence(timeout: 10) }
            if !shown { attachTree(app, "overflow-not-shown") }
            XCTAssertTrue(shown, "tapping More options did not show the overflow menu (Night mode, All text options …)")
            shot(app, "toolbar-overflow")
        }
    }

    /// Every quick sheet that has an opener on the reading chrome opens, shows the sheet container and closes again,
    /// leaving the reading host and the pane untouched (the sheet is its own window, so no pane may be recreated).
    /// Openers: title tap (the Grid quick sheet for a Bible page, as Android's `composeStartKeyChooser`) and the
    /// overflow menu's Workspaces row (the toolbar's Workspaces button only fits wide screens). The Bible / Commentary
    /// buttons are not openers here: with one or two installed documents they switch or open the full chooser instead
    /// (`QuickDocPicker`, covered by `QuickDocPickerTest`), which depends on the simulator's installed modules.
    /// History and the document sheet are opened from the drawer and the title long press; they are not covered here.
    func testEachQuickSheetOpensAndCloses() throws {
        continueAfterFailure = true
        var app = try launchReading()

        let openers: [(name: String, open: (XCUIApplication) -> Bool)] = [
            ("key chooser (title tap)", { app in
                guard let title = self.toolbarTitle(app) else { return false }
                title.tap(); return true
            }),
            ("workspaces (overflow menu)", { app in
                let more = app.buttons["More options"]; guard more.waitForExistence(timeout: 10) else { return false }
                more.tap()
                let row = app.descendants(matching: .any)
                    .matching(NSPredicate(format: "label == 'Workspaces' OR label == 'Workspaces …'")).firstMatch
                guard row.waitForExistence(timeout: 10) else { return false }
                row.tap(); return true
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

    // MARK: Task 10: Dynamic Type

    /// XXXL (the largest non-accessibility size). H2's system-text-size mapping is not on this branch, so the size is set
    /// with the launch argument; the merged close-out must re-check this with the real mapping.
    func testReadingChromeAtXXXL() throws {
        try checkReadingChrome(category: "UICTContentSizeCategoryXXXL", name: "XXXL")
    }

    /// AX5 (`AccessibilityXXXL`): every control must stay reachable; fixed-height bars may cap their scale.
    func testReadingChromeAtAX5() throws {
        try checkReadingChrome(category: "UICTContentSizeCategoryAccessibilityXXXL", name: "AX5")
    }

    /// A control that exists, is hittable (not clipped away or covered) and lies inside the window. Failures are recorded
    /// (`continueAfterFailure` is on) so one bad control does not hide the others; returns whether the control is fine.
    @discardableResult
    private func expectReachable(_ app: XCUIApplication, _ control: XCUIElement, _ what: String, _ level: String) -> Bool {
        guard control.waitForExistence(timeout: 10) else {
            attachTree(app, "\(level)-\(what)-missing")
            XCTFail("\(level): \(what) does not exist")
            return false
        }
        let window = app.windows.firstMatch.frame.insetBy(dx: -1, dy: -1)
        var ok = true
        if !control.isHittable { XCTFail("\(level): \(what) is not hittable (clipped or covered), frame \(control.frame)"); ok = false }
        if !window.contains(control.frame) { XCTFail("\(level): \(what) frame \(control.frame) is not inside the window \(window)"); ok = false }
        if !ok { attachTree(app, "\(level)-\(what)-unreachable") }
        return ok
    }

    private func checkReadingChrome(category: String, name level: String) throws {
        continueAfterFailure = true
        let sizeArguments = ["-UIPreferredContentSizeCategoryName", category]
        var app = try launchReading(extraArguments: sizeArguments)
        shot(app, "\(level)-reading")

        // Toolbar: title, home (Menu) and overflow (More options) are reachable and do not overlap each other.
        let title = toolbarTitle(app)
        let menu = app.buttons["Menu"], more = byLabel(app, "More options")
        var toolbarFrames: [(String, CGRect)] = []
        if let title = title {
            if expectReachable(app, title, "toolbar title", level) { toolbarFrames.append(("title", title.frame)) }
        } else {
            attachTree(app, "\(level)-no-toolbar-title")
            XCTFail("\(level): no toolbar title found (a static text starting with 'John' in the top strip)")
        }
        if expectReachable(app, menu, "Menu button", level) { toolbarFrames.append(("Menu", menu.frame)) }
        if expectReachable(app, more, "More options button", level) { toolbarFrames.append(("More options", more.frame)) }
        for (i, a) in toolbarFrames.enumerated() {
            for b in toolbarFrames.dropFirst(i + 1) {
                let overlap = a.1.intersection(b.1)
                XCTAssertTrue(overlap.isNull || overlap.width < 2 || overlap.height < 2,
                              "\(level): toolbar \(a.0) \(a.1) overlaps \(b.0) \(b.1)")
            }
        }

        // Drawer: the home button opens it; History, and the Task 4-6 rows (Study Pads, Backup & Restore, Device
        // synchronization), are reachable by label, scrolling the drawer if needed.
        app = try relaunchReading(app, extraArguments: sizeArguments)
        if expectReachable(app, app.buttons["Menu"], "Menu button (drawer opener)", level) {
            app.buttons["Menu"].tap()
            for row in ["History", "Study Pads", "Backup & Restore", "Device synchronization"] {
                let element = byLabel(app, row)
                guard element.waitForExistence(timeout: 10) else {
                    attachTree(app, "\(level)-drawer-no-\(row)")
                    XCTFail("\(level): drawer row '\(row)' does not exist")
                    continue
                }
                var swipes = 0
                while !element.isHittable && swipes < 4 { app.swipeUp(); swipes += 1 }
                expectReachable(app, element, "drawer row '\(row)'", level)
            }
            shot(app, "\(level)-drawer")
        }

        // Pane menu: long-press a window tab (needs two windows for Maximise to be offered).
        app = try relaunchReading(app, extraArguments: sizeArguments)
        addWindow(app, from: 1)
        if let tab = windowTabs(app).first {
            expectReachable(app, tab, "window tab", level)
            tab.press(forDuration: 1.0)
            let maximise = app.buttons.matching(NSPredicate(format: "label == 'Maximise' OR label == 'Maximise …'")).firstMatch
            expectReachable(app, maximise, "pane menu row 'Maximise'", level)
            shot(app, "\(level)-pane-menu")
        } else {
            attachTree(app, "\(level)-no-window-tab")
            XCTFail("\(level): no window tab to open the pane menu from")
        }

        // One quick sheet (the key chooser, opened by the title): its surface and Close button are reachable.
        app = try relaunchReading(app, extraArguments: sizeArguments)
        if let sheetTitle = toolbarTitle(app) {
            sheetTitle.tap()
            if quickSheetShown(app, timeout: 15) {
                expectReachable(app, quickSheet(app), "quick sheet", level)
                expectReachable(app, app.buttons["Close"], "quick sheet Close button", level)
                shot(app, "\(level)-quick-sheet")
                closeQuickSheet(app, "\(level) key chooser")
            } else {
                attachTree(app, "\(level)-sheet-not-open")
                XCTFail("\(level): tapping the title did not open the key chooser quick sheet")
            }
        } else {
            XCTFail("\(level): no toolbar title to open a quick sheet from")
        }

        // Overflow rows added in Tasks 4-6 are recorded, not asserted: they are conditional on the host (AI Actions,
        // Label settings), so absence is information for the controller, not a failure.
        app = try relaunchReading(app, extraArguments: sizeArguments)
        let overflow = byLabel(app, "More options")
        if overflow.waitForExistence(timeout: 10) {
            overflow.tap()
            _ = app.buttons["Night mode"].waitForExistence(timeout: 10)
            let present = ["AI Actions", "Label settings", "Export"].map { label -> String in
                let found = app.descendants(matching: .any)
                    .matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch.exists
                return "\(label)=\(found ? "present" : "absent")"
            }
            let a = XCTAttachment(string: present.joined(separator: " ")); a.name = "\(level)-overflow-rows"; a.lifetime = .keepAlways
            add(a)
            shot(app, "\(level)-overflow")
        }
    }
}
