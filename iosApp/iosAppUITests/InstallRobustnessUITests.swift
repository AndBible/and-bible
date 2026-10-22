import XCTest

/// H2 Task 8: killing the app during a module install must leave a consistent store. The install delays
/// `-installCommitDelayMs` after each extracted archive entry (debug binaries only), so the process can be terminated
/// mid-extraction. The relaunch uses NO reset, NO incoming fixture and NO delay: the chooser either lacks K4RawText
/// or opens it and renders Genesis 1:1. Process-kill consistency only, not power-loss durability.
/// Selectors (the "Extracting" progress text, the chooser row label) follow FeaturePathsUITests/IncomingFilesUITests;
/// a missing selector is harness discovery (tree attached), not a product RED.
final class InstallRobustnessUITests: RealAppUITestCase {
    private func require(_ condition: Bool, _ app: XCUIApplication, _ step: String,
                         file: StaticString = #filePath, line: UInt = #line) {
        if !condition { attachTree(app, step); shot(app, step) }
        XCTAssertTrue(condition, step, file: file, line: line)
    }

    private func chooserFilters(_ app: XCUIApplication) -> Bool {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Document type,'")).firstMatch.exists
    }

    /// Opens the chooser through the finite debug route and looks for the K4RawText row; false when absent after a full scan.
    private func chooserHasRow(_ app: XCUIApplication, initials: String) -> XCUIElement? {
        let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", initials + " ")).firstMatch
        let entry = element(app, "debug-open-chooser")
        if !chooserFilters(app) {
            guard entry.waitForExistence(timeout: 60) else { return nil }
            entry.tap()
            _ = waitUntil(timeout: 30) { self.chooserFilters(app) }
        }
        require(chooserFilters(app), app, "HARNESS: the document chooser is open")
        for _ in 0..<8 {
            if row.exists && row.isHittable { return row }
            nudgeList(app, up: true)
        }
        for _ in 0..<8 { nudgeList(app, up: false) }
        return row.exists && row.isHittable ? row : nil
    }

    func testKillDuringCommitLeavesConsistentStore() {
        // 1. Install the K4RawText fixture slowly through the production incoming queue, on the reading route.
        var app = launch(["-standardTestFixture", "-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16",
                          "-incomingRuntimeTest", "-incomingFixture", "sword", "-incomingRemoveFixtures",
                          "-installCommitDelayMs", "1500"])
        let message = app.staticTexts.matching(NSPredicate(format: "label == %@",
            "Do you want to install module (f-rawtext.zip)?")).firstMatch
        require(message.waitForExistence(timeout: 60), app, "install-confirm-f-rawtext.zip")
        let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        require(ok.isHittable && ok.isEnabled, app, "Unusable control: install OK")
        ok.tap()

        // 2. Wait until the debug progress marker proves the commit is mid-way (0 < percent < 100), then kill at once.
        //    The marker " [commit N%]" is appended to the status only with -installCommitDelayMs (debug seam); the
        //    fixture has 5 files, so progress is 20/40/60/80/100 with 1.5 s between steps.
        let status = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS '[commit '")).firstMatch
        func commitPercent() -> Int? {
            guard status.exists, let range = status.label.range(of: #"\[commit (\d+)%\]"#, options: .regularExpression) else { return nil }
            return Int(status.label[range].filter(\.isNumber))
        }
        require(waitUntil(timeout: 60) { if let p = commitPercent() { return p > 0 && p < 100 }; return false },
                app, "commit progress is strictly between 0 and 100 percent (mid-commit)")
        let killedAt = commitPercent() ?? -1
        shot(app, "extracting-before-kill-\(killedAt)")
        app.terminate()

        // 3. Relaunch without reset, fixture or delay: the store must be consistent.
        app = launch(["-standardTestFixture", "-featureScenario", "chooser", "-openDocument", "KJV", "-openRef", "Gen.1.1"])
        // 'Absent' is only meaningful when the chooser really lists installed modules.
        require(chooserHasRow(app, initials: "KJV") != nil, app, "HARNESS: the chooser lists KJV (so an absent K4RawText means absent)")
        let listed = chooserHasRow(app, initials: "K4RawText") != nil
        app.terminate()
        guard listed else { return } // absent: consistent
        // Listed: both an OT and an NT verse of the fixture must read.
        for (ref, text) in [("Gen.1.1", "In the beginning God created the heaven and the earth."),
                            ("John.1.1", "In the beginning was the Word")] {
            let reopened = launch(["-standardTestFixture", "-openDocument", "K4RawText", "-openRef", ref])
            let texts = reopened.webViews.staticTexts
            let shown = waitUntil(timeout: 60) {
                (0..<min(texts.count, 400)).map { texts.element(boundBy: $0).label }.joined(separator: " ").contains(text)
            }
            require(shown, reopened, "K4RawText is listed after the kill, so \(ref) must read")
            reopened.terminate()
        }
    }
}
