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

        // 2. Wait for the extracting progress text, let at least one entry land (delay is per entry), then kill.
        let extracting = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] 'Extracting'")).firstMatch
        require(extracting.waitForExistence(timeout: 60), app, "extracting progress text is shown")
        shot(app, "extracting-before-kill")
        sleep(2)
        app.terminate()

        // 3. Relaunch without reset, fixture or delay: the store must be consistent.
        app = launch(["-standardTestFixture", "-featureScenario", "chooser", "-openDocument", "KJV", "-openRef", "Gen.1.1"])
        if let row = chooserHasRow(app, initials: "K4RawText") {
            row.tap()
            let texts = app.webViews.staticTexts
            let shown = waitUntil(timeout: 60) {
                (0..<min(texts.count, 400)).map { texts.element(boundBy: $0).label }
                    .joined(separator: " ").contains("In the beginning God created the heaven and the earth.")
            }
            require(shown, app, "K4RawText is listed, so it must open and show Genesis 1:1")
        } else {
            shot(app, "k4rawtext-absent-after-kill")  // absent is the other consistent outcome
        }
        app.terminate()
    }
}
