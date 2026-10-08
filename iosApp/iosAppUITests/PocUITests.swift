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

    func testScreenshotsAllScenarios() {
        for s in ["single", "split2", "split3", "bookmarks", "history", "settings"] {
            for dark in [false, true] {
                let app = launch(s, dark: dark)
                if s.hasPrefix("s") && s != "settings" { _ = waitForVerseText(app) }
                shot(app, "\(s)-\(dark ? "dark" : "light")")
                app.terminate()
            }
        }
    }

    func testLaunchRendersVerseText() {
        XCTAssertTrue(waitForVerseText(launch("single")), "verse text not visible within 10 s")
    }

    func testScrollUpdatesToolbarTitle() {
        let app = launch("single"); _ = waitForVerseText(app)
        let title = app.staticTexts["reading-title"]
        let before = title.label
        app.webViews.firstMatch.swipeUp(velocity: .fast)
        sleep(1)
        XCTAssertNotEqual(before, title.label)
    }

    func testScrollPerformance() {
        let app = launch("single"); _ = waitForVerseText(app)
        let options = XCTMeasureOptions(); options.iterationCount = 5
        measure(metrics: [XCTOSSignpostMetric.scrollDecelerationMetric], options: options) {
            app.webViews.firstMatch.swipeUp(velocity: .fast)
            app.webViews.firstMatch.swipeDown(velocity: .fast)
        }
    }

    func testEdgeSwipeBack() {
        let app = launch("single"); _ = waitForVerseText(app)
        app.buttons["poc-open-bookmarks"].tap()
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.5)))
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 5), "edge swipe did not return to reading")
    }

    func testSplitChangesKeepWebViews() {
        let app = launch("split2"); _ = waitForVerseText(app)
        app.buttons["poc-split-toggle"].tap()
        app.buttons["poc-split-toggle"].tap()
        XCTAssertEqual(app.webViews.count, 2)
        shot(app, "split-after-toggle")
    }

    func testDynamicTypeXXXL() {
        let app = launch("bookmarks", xxxl: true); shot(app, "bookmarks-light-xxxl")
    }

    func testAccessibilityAudit() throws {
        let app = launch("single"); _ = waitForVerseText(app)
        if #available(iOS 17.0, *) {
            try app.performAccessibilityAudit { issue in
                let a = XCTAttachment(string: issue.debugDescription); a.name = "a11y-issue"; a.lifetime = .keepAlways
                self.add(a)
                return true   // record, do not fail (I0 thresholds are soft)
            }
        }
    }
}
