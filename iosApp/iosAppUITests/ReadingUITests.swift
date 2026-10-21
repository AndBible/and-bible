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

        // Surface name; whether it needs landscape on an iPhone (the toolbar fits Find and Speak only there); how many
        // element-less "potentially inaccessible text" detections of a popup / sheet window are tolerated
        // (0 = none, the surface is in the main window); opener.
        let surfaces: [(name: String, landscape: Bool, detectionCap: Int, open: (XCUIApplication) -> Bool)] = [
            ("reading with two panes (toolbar, window buttons)", false, 0, { app in
                self.addWindow(app, from: 1); return true
            }),
            ("overflow menu", false, 9, { app in
                let more = self.byLabel(app, "More options")
                guard more.waitForExistence(timeout: 10) else { return false }
                more.tap()
                return app.buttons["Night mode"].waitForExistence(timeout: 10)
            }),
            ("drawer", false, 0, { app in
                let menu = app.buttons["Menu"]
                guard menu.waitForExistence(timeout: 10) else { return false }
                menu.tap()
                return self.byLabel(app, "History").waitForExistence(timeout: 10)
            }),
            ("pane menu", false, 14, { app in
                self.addWindow(app, from: 1)
                guard let tab = self.windowTabs(app).first else { return false }
                tab.press(forDuration: 1.0)
                return app.buttons.matching(NSPredicate(format: "label == 'Maximise' OR label == 'Maximise …'"))
                    .firstMatch.waitForExistence(timeout: 10)
            }),
            ("key chooser quick sheet", false, 4, { app in
                guard let title = self.toolbarTitle(app) else { return false }
                title.tap()
                return self.quickSheetShown(app, timeout: 15)
            }),
            ("speak bar", true, 0, { app in
                let speak = self.byLabel(app, "Speak")
                guard speak.waitForExistence(timeout: 10) else { return false }
                speak.tap()
                return self.byLabel(app, "Next").waitForExistence(timeout: 15)
            }),
            ("search in place", true, 0, { app in
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
            try audit(app, surface: surface.name, detectionCap: surface.detectionCap, into: &findings)
        }

        let note = XCTAttachment(string: "Not audited: selection menu (UIKit-owned), full-screen overlay (no stable opener)")
        note.name = "a11y-not-audited"; note.lifetime = .keepAlways; add(note)
        let report = "\(findings.count) accessibility finding(s):\n" + findings.joined(separator: "\n")
        let attachment = XCTAttachment(string: report); attachment.name = "a11y-findings"; attachment.lifetime = .keepAlways
        add(attachment)
        XCTAssertTrue(findings.isEmpty, report)
    }

    /// The labels of all static texts under the pane web views, read with ONE snapshot per web view (a snapshot is a
    /// single round trip; resolving each of the 500+ word elements of an iPad pane through its own query took 0.36 s each
    /// and timed the run out). Non-empty labels only; empty when no pane web view is in the tree or a snapshot fails.
    private func bibleWebViewWords(_ app: XCUIApplication) -> Set<String> {
        func collect(_ snapshot: XCUIElementSnapshot, into words: inout Set<String>) {
            if snapshot.elementType == .staticText, !snapshot.label.isEmpty { words.insert(snapshot.label) }
            for child in snapshot.children { collect(child, into: &words) }
        }
        var words = Set<String>()
        let webViews = app.webViews.matching(NSPredicate(format: "identifier BEGINSWITH 'bible-webview-'"))
        for webView in webViews.allElementsBoundByIndex {   // a handful of panes, not the words
            if let snapshot = try? webView.snapshot() { collect(snapshot, into: &words) }
        }
        return words
    }

    /// Audits what is on screen and appends one line per finding to `findings`. The handler returns true for every issue
    /// so the audit itself does not stop at the first one; the test fails on the collected list afterwards.
    ///
    /// Filters (R3, each with its reason): Bible text words inside a pane web view (hit area only), and the visual text
    /// detection ("Potentially inaccessible text", no element) on popup / sheet windows, capped at `detectionCap`
    /// (what the Mac run showed: pane menu 14, overflow 9, key chooser sheet 4): a surplus detection is a finding and
    /// fails. Chrome findings are never filtered. Both are T14 status notes.
    @available(iOS 17.0, *)
    private func audit(_ app: XCUIApplication, surface: String, detectionCap: Int, into findings: inout [String]) throws {
        // The words of the Bible text under the pane web views, read once per surface (a per-issue query risks the
        // audit timeout). Empty when a modal window hides the panes from the tree, which is fine: no hit-area findings.
        let webViewWords = bibleWebViewWords(app)
        let knownLimitations: [(reason: String, matches: (XCUIAccessibilityAuditIssue) -> Bool)] = [
            // R3 decision (T14 status): the Bible text runs. WebKit exposes every word / punctuation mark / verse number
            // inside the pane's WKWebView as its own tiny static text (labels ',' '.' 'in' '¶' '17' ...). The text is
            // scaled by the user's own font_size_multiplier, not by chrome, and a word of running text is not a control.
            // The audit reports those elements' frames in the web content's own coordinate space (small or negative
            // values), so frame arithmetic cannot identify them; the hierarchy does: the element must be one of the
            // static texts under a pane web view (same label, tiny size). Only hit area, only static texts.
            ("Bible text word inside a pane web view (hit area of running text; scaled by font_size_multiplier)", { issue in
                guard issue.auditType == .hitRegion, let element = issue.element, element.elementType == .staticText,
                      !element.label.isEmpty, element.frame.width < 60, element.frame.height < 60 else { return false }
                return webViewWords.contains(element.label)
            }),
        ]
        var found: [String] = []
        var detectionFiltered = 0
        do {
            try app.performAccessibilityAudit { issue in
                if let limitation = knownLimitations.first(where: { $0.matches(issue) }) {
                    let a = XCTAttachment(string: "[\(surface)] filtered (\(limitation.reason)): \(issue.compactDescription)")
                    a.name = "a11y-filtered"; a.lifetime = .keepAlways; self.add(a)
                    return true
                }
                // R3 decision (T14 status): "Potentially inaccessible text" (elementDetection) is the audit's visual text
                // detection and reports no element. It fired only on surfaces drawn in their own window (overflow and
                // pane menu popups, the key chooser sheet), never on the drawer (same Compose text, main window). Every
                // row of those popups is found and tapped by label in the Task 9 tests, so the text IS in the tree.
                // Capped per surface: a detection beyond the cap is a new finding and fails.
                if issue.auditType == .elementDetection, issue.element == nil, detectionFiltered < detectionCap {
                    detectionFiltered += 1
                    let a = XCTAttachment(string: "[\(surface)] filtered detection \(detectionFiltered)/\(detectionCap) (popup window text detection): \(issue.compactDescription)")
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
            // Keep what was found so far visible before skipping.
            let partial = "audit of '\(surface)' timed out; findings so far:\n" + (findings + found).joined(separator: "\n")
            let a = XCTAttachment(string: partial); a.name = "a11y-findings-partial"; a.lifetime = .keepAlways; add(a)
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
                // The drawer is a scrollable list: rows below the fold are in the tree without a label until they
                // scroll into view, so scroll (up to 8 swipes) until the row exists by label AND is hittable.
                let element = byLabel(app, row)
                var swipes = 0
                while !(element.exists && element.isHittable) && swipes < 8 { app.swipeUp(); swipes += 1 }
                if !element.exists {
                    attachTree(app, "\(level)-drawer-no-\(row)")
                    XCTFail("\(level): drawer row '\(row)' does not exist after scrolling the drawer")
                    continue
                }
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

    // MARK: Task 11: reading completeness (R3)

    /// First element of any type whose label starts with `prefix` (labels that carry a trailing ellipsis or a count).
    private func labelStarting(_ app: XCUIApplication, _ prefix: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
    }

    /// The first pane's web view (`bible-webview-<windowId>`).
    private func firstPane(_ app: XCUIApplication) -> XCUIElement {
        app.webViews.matching(NSPredicate(format: "identifier BEGINSWITH 'bible-webview-'")).firstMatch
    }

    /// A point at the right edge of the pane, clear of the verse text (no word selection, no link).
    private func paneMargin(_ app: XCUIApplication) -> XCUICoordinate {
        firstPane(app).coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.5))
    }

    /// True while the toolbar (its home button and the overflow button) is on screen. Compose drops the toolbar from the
    /// tree in full screen (`ReadingViewScreen`: `if (!fullScreen)`), it is not merely hidden.
    private func toolbarShown(_ app: XCUIApplication) -> Bool {
        app.buttons["Menu"].exists && byLabel(app, "More options").exists
    }

    /// Opens the drawer with the home ("Menu") button and taps the row labelled `label`, scrolling the drawer when the
    /// row is below the fold. Returns false (tree attached, failure recorded) when the row cannot be reached.
    @discardableResult
    private func openDrawerRow(_ app: XCUIApplication, _ label: String) -> Bool {
        let menu = app.buttons["Menu"]
        guard menu.waitForExistence(timeout: 15) else {
            attachTree(app, "drawer-\(label)-no-menu"); XCTFail("home (Menu) button not found"); return false
        }
        menu.tap()
        let row = byLabel(app, label)
        guard row.waitForExistence(timeout: 10) else {
            attachTree(app, "drawer-no-\(label)"); XCTFail("the drawer shows no '\(label)' row"); return false
        }
        var swipes = 0
        while !row.isHittable && swipes < 8 { app.swipeUp(); swipes += 1 }
        guard row.isHittable else {
            attachTree(app, "drawer-\(label)-not-hittable"); XCTFail("drawer row '\(label)' is not reachable"); return false
        }
        row.tap()
        return true
    }

    /// Replaces the text of the single-line field of the dialog on screen (a Compose `OutlinedTextField`, exported as a
    /// text view) with `text`: tap at the right end of the field (caret after the text), wait for keyboard focus (typing
    /// right after a focus switch drops the first key), delete what is there, type, read the field back.
    private func replaceDialogText(_ app: XCUIApplication, with text: String, _ step: String) -> Bool {
        let field = app.textViews.firstMatch
        guard field.waitForExistence(timeout: 10) else {
            attachTree(app, "\(step)-no-field"); XCTFail("\(step): the dialog has no text field"); return false
        }
        let original = field.value as? String ?? ""
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.5)).tap()
        guard waitUntil(timeout: 5, { field.debugDescription.contains("Keyboard Focused") }) else {
            attachTree(app, "\(step)-no-focus"); XCTFail("\(step): the dialog field did not get keyboard focus"); return false
        }
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: original.count + 2) + text)
        let typed = waitUntil(timeout: 5) { (field.value as? String) == text }
        if !typed { attachTree(app, "\(step)-readback") }
        XCTAssertTrue(typed, "\(step): the field reads '\(field.value as? String ?? "nil")', expected '\(text)'")
        return typed
    }

    /// The single-line dialog's confirm button ("OK", `okay`).
    private func confirmDialog(_ app: XCUIApplication, _ step: String) -> Bool {
        let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        guard ok.waitForExistence(timeout: 10) else {
            attachTree(app, "\(step)-no-ok"); XCTFail("\(step): no OK button"); return false
        }
        ok.tap()
        return true
    }

    // MARK: Task 11: full screen

    /// Overflow "Fullscreen" (`toggle_fullscreen`) removes the toolbar and shows the Bible reference overlay with
    /// Android's real short-name form `KJV:Joh 3:16` (`bibleOverlayText`: "<abbreviation>:<title>" with short book names;
    /// ruling R5). A double tap on the pane leaves full screen (the chrome returns, the overlay goes), a second one
    /// enters it again by double tap (the locked path) and a third leaves.
    func testOverflowFullScreenHidesChromeAndShowsOverlay() throws {
        let app = try launchReading()
        XCTAssertTrue(toolbarShown(app), "the toolbar must be shown before full screen")
        let more = byLabel(app, "More options")
        XCTAssertTrue(more.waitForExistence(timeout: 15), "no 'More options' button")
        more.tap()
        let fullscreen = app.buttons.matching(NSPredicate(format: "label == 'Fullscreen'")).firstMatch
        guard fullscreen.waitForExistence(timeout: 10) else {
            attachTree(app, "fullscreen-no-row"); XCTFail("the overflow menu has no 'Fullscreen' row"); return
        }
        fullscreen.tap()

        let chromeGone = waitUntil(timeout: 15) { !app.buttons["Menu"].exists && !byLabel(app, "More options").exists }
        if !chromeGone { attachTree(app, "fullscreen-chrome-still-shown") }
        XCTAssertTrue(chromeGone, "the toolbar is still shown in full screen")

        // The overlay shows only while the window buttons are shown (2 s idle timer, restarted by a touch). It may have
        // timed out already, so a touch at the pane margin brings it back.
        let overlay = byLabel(app, "KJV:Joh 3:16")
        var overlayShown = overlay.waitForExistence(timeout: 5)
        if !overlayShown {
            paneMargin(app).tap()
            overlayShown = overlay.waitForExistence(timeout: 10)
        }
        if !overlayShown { attachTree(app, "fullscreen-no-overlay") }
        shot(app, "fullscreen-overlay")
        XCTAssertTrue(overlayShown, "the Bible reference overlay 'KJV:Joh 3:16' is not shown in full screen")

        // Double tap leaves full screen.
        paneMargin(app).doubleTap()
        let back = waitUntil(timeout: 15) { toolbarShown(app) }
        if !back { attachTree(app, "fullscreen-double-tap-no-exit") }
        XCTAssertTrue(back, "a double tap did not bring the toolbar back")
        XCTAssertTrue(waitUntil(timeout: 10) { !overlay.exists }, "the overlay stays after leaving full screen")

        // Double tap enters (double_tap_to_fullscreen defaults on), the next one leaves again.
        paneMargin(app).doubleTap()
        let entered = waitUntil(timeout: 15) { !app.buttons["Menu"].exists && !byLabel(app, "More options").exists }
        if !entered { attachTree(app, "fullscreen-double-tap-no-enter") }
        XCTAssertTrue(entered, "a double tap did not enter full screen")
        paneMargin(app).doubleTap()
        XCTAssertTrue(waitUntil(timeout: 15) { toolbarShown(app) }, "the second double tap did not leave full screen")
    }

    // MARK: Task 11: swipe

    /// A swipe from right to left reads on: in chapter mode (`bible_view_swipe_mode` CHAPTER, given explicitly) John 3
    /// becomes John 4 (no 'Nicodemus' in it any more), a swipe back returns to John 3.
    func testSwipeLeftOpensNextChapter() throws {
        let app = try launchReading(extraArguments: ["-bible_view_swipe_mode", "CHAPTER"])
        XCTAssertTrue(waitUntil(timeout: 20) { nicodemusInWebViews(app) == nicodemusPerPane }, "John 3 must be shown before the swipe")
        let pane = firstPane(app)
        XCTAssertTrue(pane.waitForExistence(timeout: 15), "no pane")
        pane.swipeLeft()
        let title = { self.toolbarTitle(app)?.label ?? "" }
        let next = waitUntil(timeout: 30) { title().hasPrefix("John 4") && nicodemusInWebViews(app) == 0 }
        if !next { attachTree(app, "swipe-left-not-john-4") }
        shot(app, "swipe-left")
        XCTAssertTrue(next, "after a left swipe the title is '\(title())' and 'Nicodemus' appears \(nicodemusInWebViews(app)) times; expected John 4 without Nicodemus")

        pane.swipeRight()
        let backAgain = waitUntil(timeout: 30) { title().hasPrefix("John 3") && nicodemusInWebViews(app) == nicodemusPerPane }
        if !backAgain { attachTree(app, "swipe-right-not-john-3") }
        XCTAssertTrue(backAgain, "after a right swipe the title is '\(title())'; expected John 3 again")
    }

    // MARK: Task 11: workspaces

    /// Overflow "Workspaces" -> "Manage workspaces…" opens the selector (title bar "New" action, per-row "Menu").
    private func openWorkspaceSelector(_ app: XCUIApplication, _ step: String) -> Bool {
        let more = byLabel(app, "More options")
        guard more.waitForExistence(timeout: 15) else {
            attachTree(app, "\(step)-no-overflow"); XCTFail("\(step): no 'More options' button"); return false
        }
        more.tap()
        let row = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == 'Workspaces' OR label == 'Workspaces …'")).firstMatch
        guard row.waitForExistence(timeout: 10) else {
            attachTree(app, "\(step)-no-workspaces-row"); XCTFail("\(step): no 'Workspaces' overflow row"); return false
        }
        row.tap()
        let manage = labelStarting(app, "Manage workspaces")
        guard manage.waitForExistence(timeout: 15) else {
            attachTree(app, "\(step)-no-manage"); XCTFail("\(step): the workspaces sheet has no 'Manage workspaces' row"); return false
        }
        manage.tap()
        guard byLabel(app, "New").waitForExistence(timeout: 15) else {
            attachTree(app, "\(step)-selector-not-shown"); XCTFail("\(step): the workspace selector did not open (no 'New' action)"); return false
        }
        return true
    }

    /// The row menu ("Menu") button of the selector row whose label starts with `name`: the nearest one to the row.
    private func workspaceRowMenu(_ app: XCUIApplication, _ name: String, _ step: String) -> XCUIElement? {
        let row = labelStarting(app, name)
        guard row.waitForExistence(timeout: 15) else {
            attachTree(app, "\(step)-no-row"); XCTFail("\(step): no workspace row '\(name)'"); return nil
        }
        let y = row.frame.midY
        let menus = app.buttons.matching(NSPredicate(format: "label == 'Menu'")).allElementsBoundByIndex.filter { $0.frame.height > 0 }
        guard let nearest = menus.min(by: { abs($0.frame.midY - y) < abs($1.frame.midY - y) }) else {
            attachTree(app, "\(step)-no-row-menu"); XCTFail("\(step): row '\(name)' has no menu button"); return nil
        }
        return nearest
    }

    /// Taps the exact-label item of the open dropdown (`Rename`, `Remove`).
    private func tapRowMenuItem(_ app: XCUIApplication, _ label: String, _ step: String) -> Bool {
        let item = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
        guard item.waitForExistence(timeout: 10) else {
            attachTree(app, "\(step)-no-\(label)"); XCTFail("\(step): the row menu has no '\(label)'"); return false
        }
        item.tap()
        return true
    }

    /// Creates a workspace in the selector; the selector then leaves with the new workspace current
    /// (`WorkspaceSelectorController.createNew` ends in `selectWorkspace`, as Android's `goToWorkspace`).
    private func createWorkspace(_ app: XCUIApplication, named name: String) -> Bool {
        byLabel(app, "New").tap()
        guard app.staticTexts.matching(NSPredicate(format: "label == 'Give name for new workspace'")).firstMatch
            .waitForExistence(timeout: 10) else {
            attachTree(app, "create-\(name)-no-dialog"); XCTFail("no 'Give name for new workspace' dialog"); return false
        }
        guard replaceDialogText(app, with: name, "create \(name)"), confirmDialog(app, "create \(name)") else { return false }
        let left = element(app, "reading-host").waitForExistence(timeout: 30) && !byLabel(app, "New").exists
        if !left { attachTree(app, "create-\(name)-selector-stays") }
        XCTAssertTrue(left, "creating '\(name)' did not return to the reading view")
        return left
    }

    /// Create, rename and delete in the workspace selector, each checked in the list and after Save in the workspaces
    /// quick sheet: Alpha is created (becomes current), renamed to Beta and saved; Gamma is created (current); Beta, now
    /// not current, is removed and saved; only Gamma of the three remains. Labels: `give_name_workspace`, `rename`,
    /// `delete_workspace` ("Remove"), `save_and_exit` ("Save"), `new_item` ("New"), `menu`, `workspace_listing_with_current`.
    func testWorkspaceCreateRenameDelete() throws {
        continueAfterFailure = false
        let app = try launchReading()

        // Create Alpha.
        guard openWorkspaceSelector(app, "open 1") else { return }
        guard createWorkspace(app, named: "UITest Alpha") else { return }

        // Rename Alpha to Beta and Save.
        guard openWorkspaceSelector(app, "open 2") else { return }
        XCTAssertTrue(labelStarting(app, "UITest Alpha").waitForExistence(timeout: 10), "the created workspace is not listed")
        guard let alphaMenu = workspaceRowMenu(app, "UITest Alpha", "rename") else { return }
        alphaMenu.tap()
        guard tapRowMenuItem(app, "Rename", "rename") else { return }
        guard replaceDialogText(app, with: "UITest Beta", "rename"), confirmDialog(app, "rename") else { return }
        XCTAssertTrue(labelStarting(app, "UITest Beta").waitForExistence(timeout: 10), "the renamed row 'UITest Beta' is not listed")
        XCTAssertFalse(labelStarting(app, "UITest Alpha").exists, "the old name 'UITest Alpha' is still listed after the rename")
        shot(app, "workspace-renamed")
        let save = app.buttons.matching(NSPredicate(format: "label == 'Save'")).firstMatch
        XCTAssertTrue(save.waitForExistence(timeout: 10), "no 'Save' button")
        XCTAssertTrue(save.isEnabled, "'Save' stays disabled after a rename (the selector is not dirty)")
        save.tap()
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 30), "Save did not return to the reading view")

        // Create Gamma (current), then delete Beta (not current) and Save.
        guard openWorkspaceSelector(app, "open 3") else { return }
        XCTAssertTrue(labelStarting(app, "UITest Beta").waitForExistence(timeout: 10), "the rename was not kept: 'UITest Beta' is not listed")
        XCTAssertFalse(labelStarting(app, "UITest Alpha").exists, "'UITest Alpha' is back after the rename was saved")
        guard createWorkspace(app, named: "UITest Gamma") else { return }
        guard openWorkspaceSelector(app, "open 4") else { return }
        guard let betaMenu = workspaceRowMenu(app, "UITest Beta", "delete") else { return }
        betaMenu.tap()
        guard tapRowMenuItem(app, "Remove", "delete") else { return }
        let gone = waitUntil(timeout: 10) { !labelStarting(app, "UITest Beta").exists }
        if !gone { attachTree(app, "delete-row-stays") }
        XCTAssertTrue(gone, "'UITest Beta' is still listed after Remove")
        XCTAssertTrue(labelStarting(app, "UITest Gamma").exists, "removing Beta removed Gamma too")
        let save2 = app.buttons.matching(NSPredicate(format: "label == 'Save'")).firstMatch
        XCTAssertTrue(save2.waitForExistence(timeout: 10) && save2.isEnabled, "'Save' is not enabled after a Remove")
        save2.tap()
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 30), "Save did not return to the reading view after the delete")

        // The result is persisted: the quick switch list shows Gamma only.
        let more = byLabel(app, "More options")
        XCTAssertTrue(more.waitForExistence(timeout: 15))
        more.tap()
        let row = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == 'Workspaces' OR label == 'Workspaces …'")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 10), "no 'Workspaces' overflow row")
        row.tap()
        XCTAssertTrue(quickSheetShown(app, timeout: 15), "the workspaces quick sheet did not open")
        XCTAssertTrue(labelStarting(app, "UITest Gamma").waitForExistence(timeout: 10), "'UITest Gamma' is not in the workspace list")
        XCTAssertFalse(labelStarting(app, "UITest Beta").exists, "the removed 'UITest Beta' is still in the workspace list")
        XCTAssertFalse(labelStarting(app, "UITest Alpha").exists, "the renamed 'UITest Alpha' is still in the workspace list")
        shot(app, "workspace-final-list")
    }

    // MARK: Task 11: reading progress

    /// Drawer "Read/Memory Progress" opens the screen (Reading / Memorization tabs, the Old and New Testament book
    /// grids); the "Joh" cell opens John's chapter grid and chapter "4" returns to the reading view on John 4 (the chapter
    /// answer opens in the asking window). Labels: `reading_progress_title`, `memorize_tab_*`,
    /// `reading_progress_old_testament`, `reading_progress_new_testament`; the cell labels are the books' short names.
    func testReadingProgressFromDrawer() throws {
        let app = try launchReading()
        guard openDrawerRow(app, "Read/Memory Progress") else { return }
        for expected in ["Old Testament", "New Testament"] {
            let shown = app.staticTexts.matching(NSPredicate(format: "label == %@", expected)).firstMatch.waitForExistence(timeout: 20)
            if !shown { attachTree(app, "progress-no-\(expected)") }
            XCTAssertTrue(shown, "the progress screen shows no '\(expected)' heading")
        }
        XCTAssertTrue(labelStarting(app, "Memorization").exists, "no Memorization tab")
        shot(app, "reading-progress")

        // John's cell (NT grid): scroll it into view if needed.
        let john = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Joh'")).firstMatch
        var swipes = 0
        while !(john.exists && john.isHittable) && swipes < 6 { app.swipeUp(); swipes += 1 }
        guard john.exists && john.isHittable else {
            attachTree(app, "progress-no-john-cell"); XCTFail("no hittable 'Joh' book cell in the New Testament grid"); return
        }
        john.tap()

        // The chapter grid: the lowest cell labelled "4" (the grid sits above the calendar, whose day numbers come after).
        let chapter4 = { () -> XCUIElement? in
            app.descendants(matching: .any).matching(NSPredicate(format: "label == '4'")).allElementsBoundByIndex
                .filter { $0.frame.height > 0 && $0.isHittable }.min(by: { $0.frame.minY < $1.frame.minY })
        }
        let found = waitUntil(timeout: 15) { chapter4() != nil }
        guard found, let cell = chapter4() else {
            attachTree(app, "progress-no-chapter-4"); XCTFail("John's chapter grid shows no hittable chapter '4'"); return
        }
        cell.tap()
        let reading = element(app, "reading-host").waitForExistence(timeout: 30)
        if !reading { attachTree(app, "progress-chapter-no-reading") }
        XCTAssertTrue(reading, "tapping chapter 4 did not return to the reading view")
        let onFour = waitUntil(timeout: 30) { self.toolbarTitle(app)?.label.hasPrefix("John 4") == true && nicodemusInWebViews(app) == 0 }
        if !onFour { attachTree(app, "progress-chapter-not-john-4") }
        XCTAssertTrue(onFour, "the reading view shows '\(toolbarTitle(app)?.label ?? "no title")', expected John 4")
    }

    // MARK: Task 11: in-place search

    /// Drawer "Find" opens the in-place search field; "Nicodemus" + Return runs the search (creating the KJV index first
    /// when the prompt "An index is required to search" appears: "Create"), the sheet shows "<n> verses in 1
    /// translations" with n > 0 and result cards; the card of John 7:50 (or 19:39) opens that chapter (a chapter with
    /// one 'Nicodemus', unlike John 3 with three).
    func testInPlaceSearchShowsResults() throws {
        let app = try launchReading()
        guard openDrawerRow(app, "Find") else { return }
        let field = element(app, "reading-search-field")
        guard field.waitForExistence(timeout: 15) else {
            attachTree(app, "search-no-field"); XCTFail("the in-place search field did not open"); return
        }
        field.tap()
        XCTAssertTrue(waitUntil(timeout: 5) { field.debugDescription.contains("Keyboard Focused") }, "the search field has no keyboard focus")
        field.typeText("Nicodemus")
        XCTAssertTrue(waitUntil(timeout: 5) { (field.value as? String) == "Nicodemus" }, "the search field reads '\(field.value as? String ?? "nil")'")
        field.typeText("\n")

        let countLabel = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label MATCHES %@", "[0-9]+ verses in [0-9]+ translations")).firstMatch
        let create = app.buttons.matching(NSPredicate(format: "label == 'Create'")).firstMatch
        // Either the results arrive, or the index prompt comes first (a fresh simulator has no KJV index).
        _ = waitUntil(timeout: 60) { countLabel.exists || create.exists }
        if !countLabel.exists && create.exists {
            shot(app, "search-index-prompt")
            create.tap()
        }
        let results = countLabel.waitForExistence(timeout: 300)
        if !results { attachTree(app, "search-no-results"); shot(app, "search-no-results") }
        XCTAssertTrue(results, "no result count ('<n> verses in <m> translations') after searching 'Nicodemus'")
        guard results else { return }
        let total = Int(countLabel.label.split(separator: " ").first ?? "") ?? 0
        XCTAssertGreaterThan(total, 0, "the search found no verse: '\(countLabel.label)'")
        shot(app, "search-results")

        // A result card that leads away from John 3: John 7:50 or John 19:39 (each card is a clickable card whose
        // label starts with its reference).
        var target: XCUIElement?
        for _ in 0..<8 where target == nil {
            for ref in ["7:50", "19:39"] {
                let card = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@ AND label CONTAINS 'Nicodemus'", ref)).firstMatch
                if card.exists && card.isHittable { target = card; break }
            }
            if target == nil { nudgeSheetList(app) }
        }
        guard let card = target else {
            attachTree(app, "search-no-john-7-50-card"); XCTFail("no result card for John 7:50 or John 19:39 (total \(total))"); return
        }
        let expectedChapter = card.label.contains("7:50") ? "John 7" : "John 19"
        card.tap()
        let opened = waitUntil(timeout: 30) {
            self.toolbarTitle(app)?.label.hasPrefix(expectedChapter) == true || nicodemusInWebViews(app) == 1
        }
        if !opened { attachTree(app, "search-card-did-not-open"); shot(app, "search-card-did-not-open") }
        XCTAssertTrue(opened, "tapping the card did not open \(expectedChapter) (title '\(toolbarTitle(app)?.label ?? "none")', Nicodemus x\(nicodemusInWebViews(app)))")
    }

    // MARK: Task 11: selection menu

    /// Long-presses the first 'Nicodemus' of the chapter: the page selects the word and UIKit shows the edit menu with
    /// the AndBible rows ahead of the system ones.
    private func longPressNicodemus(_ app: XCUIApplication) -> Bool {
        let word = app.webViews.staticTexts.matching(NSPredicate(format: "label == 'Nicodemus'")).firstMatch
        guard word.waitForExistence(timeout: 20) else { attachTree(app, "selection-no-word"); XCTFail("no 'Nicodemus' text to select"); return false }
        word.press(forDuration: 1.2)
        return true
    }

    /// An item of the edit menu by exact label. The menu is paged on a narrow screen, so the next-page chevron is tried
    /// when the item is not on the first page.
    private func editMenuItem(_ app: XCUIApplication, _ label: String, timeout: TimeInterval = 12) -> XCUIElement? {
        let item = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
        let next = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == 'show.next.items.menu.button' OR label == 'Show more items'")).firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        var pages = 0
        while Date() < deadline {
            if item.exists { return item }
            if next.exists && next.isHittable && pages < 4 { next.tap(); pages += 1 }
            usleep(500_000)
        }
        return item.exists ? item : nil
    }

    /// Two-step bookmarking (`disable_two_step_bookmarking` off, the default): the menu of a verse selection on a Bible
    /// has the single "Bookmark" row (`add_bookmark1`), not the step-two rows; choosing it shows "Selection"
    /// (`add_bookmark3`) and "Verses" (`add_bookmark_whole_verse1`). With the setting on both rows are in the first menu
    /// and "Bookmark" is not. The UIKit edit menu is driven by a long press on a word and read through the accessibility
    /// tree; if the Mac run shows the rows are not exposed, this test records the tree.
    func testTwoStepBookmarkFromSelection() throws {
        continueAfterFailure = true
        var app = try launchReading()
        XCTContext.runActivity(named: "two-step (default)") { _ in
            guard longPressNicodemus(app) else { return }
            guard let bookmark = editMenuItem(app, "Bookmark") else {
                attachTree(app, "two-step-no-bookmark-row"); shot(app, "two-step-no-bookmark-row")
                XCTFail("the selection menu has no 'Bookmark' row"); return
            }
            shot(app, "two-step-first-menu")
            XCTAssertFalse(app.descendants(matching: .any).matching(NSPredicate(format: "label == 'Selection'")).firstMatch.exists,
                           "step-two row 'Selection' is shown before 'Bookmark' was chosen")
            XCTAssertFalse(app.descendants(matching: .any).matching(NSPredicate(format: "label == 'Verses'")).firstMatch.exists,
                           "step-two row 'Verses' is shown before 'Bookmark' was chosen")
            bookmark.tap()
            let selection = editMenuItem(app, "Selection"), verses = editMenuItem(app, "Verses")
            if selection == nil || verses == nil { attachTree(app, "two-step-no-step-two"); shot(app, "two-step-no-step-two") }
            XCTAssertNotNil(selection, "step two has no 'Selection' row")
            XCTAssertNotNil(verses, "step two has no 'Verses' row")
            shot(app, "two-step-second-menu")
        }

        app = (try? relaunchReading(app, extraArguments: ["-disable_two_step_bookmarking", "YES"])) ?? app
        XCTContext.runActivity(named: "one-step (disable_two_step_bookmarking)") { _ in
            guard longPressNicodemus(app) else { return }
            let selection = editMenuItem(app, "Selection"), verses = editMenuItem(app, "Verses")
            if selection == nil || verses == nil { attachTree(app, "one-step-no-rows"); shot(app, "one-step-no-rows") }
            XCTAssertNotNil(selection, "with two-step bookmarking off the first menu has no 'Selection' row")
            XCTAssertNotNil(verses, "with two-step bookmarking off the first menu has no 'Verses' row")
            XCTAssertFalse(app.descendants(matching: .any).matching(NSPredicate(format: "label == 'Bookmark'")).firstMatch.exists,
                           "the single 'Bookmark' row is shown although two-step bookmarking is off")
            shot(app, "one-step-menu")
        }
    }

    // MARK: Task 11: drawer rows

    /// The R3 drawer rows open their screens: Study Pads (the label manager: title and the "New" action), Backup &
    /// Restore (its "Backup to…" and "Restore or Import from…" buttons) and Device synchronization (the
    /// "Synchronization Backend" row). A fresh process for each row.
    func testDrawerStudyPadsBackupAndCloudSyncOpen() throws {
        continueAfterFailure = true
        var app = try launchReading()

        XCTContext.runActivity(named: "Study Pads") { _ in
            guard openDrawerRow(app, "Study Pads") else { return }
            let new = byLabel(app, "New")
            let shown = new.waitForExistence(timeout: 20)
            if !shown { attachTree(app, "studypads-not-shown") }
            XCTAssertTrue(shown, "Study Pads did not open the label manager (no 'New' action)")
            XCTAssertFalse(element(app, "reading-host").exists && byLabel(app, "More options").exists, "the reading toolbar is still shown over the Study Pads screen")
            shot(app, "drawer-studypads")
        }

        app = (try? relaunchReading(app)) ?? app
        XCTContext.runActivity(named: "Backup & Restore") { _ in
            guard openDrawerRow(app, "Backup & Restore") else { return }
            let backup = labelStarting(app, "Backup to")
            let shown = backup.waitForExistence(timeout: 20)
            if !shown { attachTree(app, "backup-not-shown") }
            XCTAssertTrue(shown, "Backup & Restore did not open (no 'Backup to…' button)")
            XCTAssertTrue(labelStarting(app, "Restore or Import from").exists, "no 'Restore or Import from…' button")
            shot(app, "drawer-backup")
        }

        app = (try? relaunchReading(app)) ?? app
        XCTContext.runActivity(named: "Device synchronization") { _ in
            guard openDrawerRow(app, "Device synchronization") else { return }
            let backend = labelStarting(app, "Synchronization Backend")
            let shown = backend.waitForExistence(timeout: 20)
            if !shown { attachTree(app, "sync-not-shown") }
            XCTAssertTrue(shown, "Device synchronization did not open (no 'Synchronization Backend' row)")
            shot(app, "drawer-sync")
        }
    }

    // MARK: Task 11: pane menu exports

    /// A StudyPad window's pane menu carries the export rows `export_fileformat` "Export as HTML",
    /// `export_something` + `studypad` "Export Study Pad" and `export_bookmarks_csv` "Export Bookmarks as CSV" (the rows
    /// that open a dialog may carry a trailing " …"); the Bible window's menu has none of them. The StudyPad is made
    /// through the drawer: Study Pads, "New" (the editor proposes the name "Label N"), "OK", then its row.
    func testPaneMenuExportRowsOnAStudyPad() throws {
        let app = try launchReading()
        func menuRow(_ label: String) -> XCUIElement {
            app.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", label, label + " …")).firstMatch
        }
        func openPaneMenu() -> Bool {
            let window = byLabel(app, "Window menu")
            guard window.waitForExistence(timeout: 15) else {
                attachTree(app, "pane-menu-no-window-button"); XCTFail("no pane 'Window menu' button"); return false
            }
            window.tap()
            return true
        }

        // The Bible window first: none of the export rows.
        guard openPaneMenu() else { return }
        XCTAssertTrue(menuRow("Maximise").waitForExistence(timeout: 10) || menuRow("Close").waitForExistence(timeout: 5), "the pane menu did not open")
        for row in ["Export as HTML", "Export Study Pad", "Export Bookmarks as CSV"] {
            XCTAssertFalse(menuRow(row).exists, "the Bible window's pane menu shows '\(row)'")
        }
        shot(app, "pane-menu-bible")
        app.terminate()

        // A StudyPad in the window.
        let app2 = try launchReading()
        guard openDrawerRow(app2, "Study Pads") else { return }
        let new = byLabel(app2, "New")
        guard new.waitForExistence(timeout: 20) else { attachTree(app2, "studypad-no-new"); XCTFail("no 'New' action in the Study Pads manager"); return }
        new.tap()
        let ok = app2.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        guard ok.waitForExistence(timeout: 20) else { attachTree(app2, "studypad-no-editor"); XCTFail("the label editor did not open (no 'OK')"); return }
        ok.tap()
        let row = app2.descendants(matching: .any).matching(NSPredicate(format: "label MATCHES %@", "Label [0-9]+.*")).firstMatch
        guard row.waitForExistence(timeout: 20) else { attachTree(app2, "studypad-no-row"); XCTFail("the new Study Pad 'Label N' is not listed"); return }
        row.tap()
        guard element(app2, "reading-host").waitForExistence(timeout: 30) else { attachTree(app2, "studypad-no-reading"); XCTFail("choosing the Study Pad did not return to the reading view"); return }
        // The StudyPad document replaces the Bible in the window: the chapter text is gone.
        XCTAssertTrue(waitUntil(timeout: 30) { nicodemusInWebViews(app2) == 0 }, "the window still shows John 3 after opening the Study Pad")

        let window = byLabel(app2, "Window menu")
        guard window.waitForExistence(timeout: 15) else { attachTree(app2, "studypad-no-window-button"); XCTFail("no pane 'Window menu' button on the Study Pad window"); return }
        window.tap()
        for label in ["Export as HTML", "Export Study Pad", "Export Bookmarks as CSV"] {
            let row = app2.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", label, label + " …")).firstMatch
            let shown = row.waitForExistence(timeout: 10)
            if !shown { attachTree(app2, "studypad-menu-no-\(label)"); shot(app2, "studypad-menu") }
            XCTAssertTrue(shown, "the Study Pad window's pane menu has no '\(label)' row")
        }
        shot(app2, "pane-menu-studypad")
    }
}
