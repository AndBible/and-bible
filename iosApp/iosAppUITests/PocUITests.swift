import XCTest

/// I0 measurements (spec §6.2). Thresholds are deliberately soft: record, do not gate.
final class PocUITests: XCTestCase {
    override func setUp() { continueAfterFailure = true }

    private func launch(_ scenario: String, dark: Bool = false, xxxl: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchEnvironment["SCREENSHOT_SCENARIO"] = scenario
        app.launchEnvironment["POC_DARK"] = dark ? "1" : "0"
        if xxxl { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
        app.launch()
        return app
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let a = XCTAttachment(screenshot: app.screenshot()); a.name = name; a.lifetime = .keepAlways; add(a)
    }

    private func waitForVerseText(_ app: XCUIApplication, timeout: TimeInterval = 10) -> Bool {
        app.webViews.staticTexts.containing(NSPredicate(format: "label CONTAINS 'quickened'")).firstMatch
            .waitForExistence(timeout: timeout)
    }

    /// Polls until `condition` holds or `timeout` elapses; returns the final result.
    private func waitUntil(timeout: TimeInterval = 5, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            usleep(100_000)
        }
        return condition()
    }

    /// An element that is always present once the scenario's route has rendered: the split toggle on the
    /// reading route, the back button on every other route.
    private func chromeElement(_ app: XCUIApplication, reading: Bool) -> XCUIElement {
        reading ? app.buttons["poc-split-toggle"] : app.buttons["poc-back"]
    }

    func testScreenshotsAllScenarios() {
        let readingScenarios: Set<String> = ["single", "split2", "split3"]
        for s in ["single", "split2", "split3", "bookmarks", "history", "settings"] {
            for dark in [false, true] {
                let app = launch(s, dark: dark)
                let reading = readingScenarios.contains(s)
                XCTAssertTrue(chromeElement(app, reading: reading).waitForExistence(timeout: 10),
                              "\(s): screen chrome not visible within 10 s")
                if reading {
                    let windows = s == "split3" ? 3 : (s == "split2" ? 2 : 1)
                    XCTAssertTrue(verseTextInEachWebView(app, count: windows),
                                  "\(s): verse text not visible in all \(windows) web views within 10 s")
                }
                shot(app, "\(s)-\(dark ? "dark" : "light")")
                app.terminate()
            }
        }
    }

    func testLaunchRendersVerseText() {
        XCTAssertTrue(waitForVerseText(launch("single")), "verse text not visible within 10 s")
    }

    func testScrollUpdatesToolbarTitle() {
        let app = launch("single"); XCTAssertTrue(waitForVerseText(app), "verse text not visible within 10 s")
        let title = app.staticTexts["reading-title"]
        let before = title.label
        app.webViews.firstMatch.swipeUp(velocity: .fast)
        sleep(1)
        XCTAssertNotEqual(before, title.label)
    }

    func testScrollPerformance() {
        let app = launch("single"); XCTAssertTrue(waitForVerseText(app), "verse text not visible within 10 s")
        let options = XCTMeasureOptions(); options.iterationCount = 5
        measure(metrics: [XCTOSSignpostMetric.scrollDecelerationMetric], options: options) {
            app.webViews.firstMatch.swipeUp(velocity: .fast)
            app.webViews.firstMatch.swipeDown(velocity: .fast)
        }
    }

    func testEdgeSwipeBack() {
        let app = launch("single"); XCTAssertTrue(waitForVerseText(app), "verse text not visible within 10 s")
        app.buttons["poc-open-bookmarks"].tap()
        XCTAssertTrue(app.buttons["poc-back"].waitForExistence(timeout: 5), "bookmarks route not reached")
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.5)),
                    withVelocity: .fast, thenHoldForDuration: 0)
        XCTAssertTrue(waitUntil { !app.buttons["poc-back"].exists }, "edge swipe did not leave bookmarks")
        XCTAssertTrue(app.buttons["poc-split-toggle"].waitForExistence(timeout: 5), "edge swipe did not return to reading")
    }

    /// The distinct pane identifiers (`bible-webview-<windowId>`, set on each WKWebView) among the WebView
    /// elements. XCUITest reports several WebView-type elements per WKWebView (3 per pane in I0 CI), so a bare
    /// `app.webViews.count` over-counts.
    private func paneIds(_ app: XCUIApplication) -> [String] {
        let ids = app.webViews.matching(NSPredicate(format: "identifier BEGINSWITH 'bible-webview-'"))
            .allElementsBoundByIndex.map(\.identifier)
        return Array(Set(ids)).sorted()
    }

    /// Records the element tree when a pane count is wrong, so the next CI run shows the hierarchy.
    private func attachTree(_ app: XCUIApplication, _ step: String) {
        let a = XCTAttachment(string: app.debugDescription); a.name = "tree-\(step)"; a.lifetime = .keepAlways; add(a)
    }

    /// True once every one of the first `count` panes shows the verse text. A re-parented or freshly
    /// created but blank WKWebView therefore fails, unlike a bare count check.
    private func verseTextInEachWebView(_ app: XCUIApplication, count: Int, timeout: TimeInterval = 10) -> Bool {
        guard waitUntil(timeout: timeout, { paneIds(app).count >= count }) else { return false }
        for id in paneIds(app).prefix(count) {
            let verse = app.webViews.matching(identifier: id).firstMatch.staticTexts
                .containing(NSPredicate(format: "label CONTAINS 'quickened'")).firstMatch
            if !verse.waitForExistence(timeout: timeout) { return false }
        }
        return true
    }

    private func assertPaneCount(_ app: XCUIApplication, _ count: Int, _ step: String) {
        let ok = waitUntil { paneIds(app).count == count }
        XCTAssertTrue(ok, "\(step): expected \(count) panes, got \(paneIds(app)) (webViews.count \(app.webViews.count))")
        if !ok { attachTree(app, step) }
    }

    private func toggleSplit(_ app: XCUIApplication, expecting count: Int, _ step: String) {
        app.buttons["poc-split-toggle"].tap()
        assertPaneCount(app, count, step)
        XCTAssertTrue(verseTextInEachWebView(app, count: count), "\(step): verse text missing in a web view")
    }

    /// reading -> bookmarks -> back: the web views are re-parented; they must still show their text.
    private func roundTripThroughBookmarks(_ app: XCUIApplication, windows: Int, _ step: String) {
        app.buttons["poc-open-bookmarks"].tap()
        XCTAssertTrue(app.buttons["poc-back"].waitForExistence(timeout: 5), "\(step): bookmarks route not reached")
        app.buttons["poc-back"].tap()
        XCTAssertTrue(app.buttons["poc-split-toggle"].waitForExistence(timeout: 5), "\(step): did not return to reading")
        assertPaneCount(app, windows, step)
        XCTAssertTrue(verseTextInEachWebView(app, count: windows), "\(step): verse text missing after returning from bookmarks")
    }

    func testSplitChangesKeepWebViews() {
        let app = launch("split2")
        XCTAssertTrue(verseTextInEachWebView(app, count: 2), "split2: verse text not visible in both web views")
        toggleSplit(app, expecting: 3, "2->3")
        toggleSplit(app, expecting: 2, "3->2")
        toggleSplit(app, expecting: 3, "2->3 again")
        toggleSplit(app, expecting: 2, "3->2 again")
        roundTripThroughBookmarks(app, windows: 2, "split2 bookmarks round trip")
        shot(app, "split-after-toggle")
    }

    func testReparentKeepsVerseTextSingle() {
        let app = launch("single")
        XCTAssertTrue(waitForVerseText(app), "verse text not visible within 10 s")
        roundTripThroughBookmarks(app, windows: 1, "single bookmarks round trip")
    }

    func testDynamicTypeXXXL() {
        let app = launch("bookmarks", xxxl: true)
        XCTAssertTrue(app.buttons["poc-back"].waitForExistence(timeout: 10), "bookmarks screen not visible within 10 s")
        shot(app, "bookmarks-light-xxxl")
    }

    func testAccessibilityAudit() throws {
        let app = launch("single"); XCTAssertTrue(waitForVerseText(app), "verse text not visible within 10 s")
        if #available(iOS 17.0, *) {
            try app.performAccessibilityAudit { issue in
                let a = XCTAttachment(string: issue.debugDescription); a.name = "a11y-issue"; a.lifetime = .keepAlways
                self.add(a)
                return true   // record, do not fail (I0 thresholds are soft)
            }
        }
    }
}
