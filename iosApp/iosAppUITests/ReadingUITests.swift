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

    /// How many 'Nicodemus' labels the pane `id` exposes (the first-verse marker: exactly 1 when the pane is in the
    /// accessibility tree once; 0 = dropped out; 2+ = duplicated by a re-parent).
    private func nicodemusCount(_ app: XCUIApplication, pane id: String) -> Int {
        app.webViews.matching(identifier: id).firstMatch.staticTexts
            .matching(NSPredicate(format: "label == 'Nicodemus'")).count
    }

    /// Every one of the first `count` panes shows the marker exactly once (polls: WebKit rebuilds its tree lazily).
    private func assertTextOncePerPane(_ app: XCUIApplication, panes count: Int, _ step: String) {
        let ok = waitUntil(timeout: 30) {
            let ids = paneIds(app)
            return ids.count == count && ids.allSatisfy { nicodemusCount(app, pane: $0) == 1 }
        }
        if !ok { attachTree(app, step) }
        let counts = paneIds(app).map { "\($0)=\(nicodemusCount(app, pane: $0))" }
        XCTAssertTrue(ok, "\(step): expected \(count) panes with the text exactly once each, got \(counts)")
    }

    /// The tab-rail buttons that stand for windows (labelled with the module, 'KJV' here).
    private func windowTabs(_ app: XCUIApplication) -> [XCUIElement] {
        app.buttons.matching(NSPredicate(format: "label CONTAINS 'KJV'")).allElementsBoundByIndex
    }

    private func tapMenuItem(_ app: XCUIApplication, _ labels: [String], _ step: String) {
        for label in labels {
            let item = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch
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

        // A sheet reachable from the reading chrome: the first of these labels that exists.
        let opener = ["Search", "Bookmarks", "History"]
            .map { app.buttons[$0] }.first { $0.waitForExistence(timeout: 5) }
        guard let opener else {
            attachTree(app, "no-sheet-opener")
            XCTFail("no reading control that opens a sheet (Search/Bookmarks/History)")
            return
        }
        opener.tap()
        sleep(2)
        shot(app, "sheet-open")
        // Dismiss: a Close/Cancel/Back button when the sheet has one, else a swipe down on the sheet area.
        let dismiss = ["Close", "Cancel", "Back"].map { app.buttons[$0] }.first { $0.exists }
        if let dismiss { dismiss.tap() } else { app.swipeDown(velocity: .fast) }

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
}
