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
                if reading { XCTAssertTrue(waitForVerseText(app), "\(s): verse text not visible within 10 s") }
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

    func testSplitChangesKeepWebViews() {
        let app = launch("split2"); XCTAssertTrue(waitForVerseText(app), "verse text not visible within 10 s")
        app.buttons["poc-split-toggle"].tap()
        app.buttons["poc-split-toggle"].tap()
        XCTAssertTrue(waitUntil { app.webViews.count == 2 }, "expected 2 web views after toggling, got \(app.webViews.count)")
        shot(app, "split-after-toggle")
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
