import XCTest

/// Real-app flows (I2 phase 2 join): fresh install -> Welcome -> Easy start download -> reading; relaunch; iPad.
/// Uses the debug-only launch arguments `-resetData` (fresh install) and `-openRef <osisRef>` (see
/// `IosDebugLaunchArgs.kt`). XCTest runs the tests alphabetically: testFreshInstall..., testIpad..., testRelaunch....
/// `testRelaunchReopensWorkspace` needs the data the Fresh test left; on iPhone the iPad test is skipped (no wipe) so
/// the order works; on iPad the iPad test wipes the data first and the Relaunch test skips (no installed data).
final class AppUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

    private func launch(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-task6ResetCrashState", "-discrete_mode", "NO", "-show_calculator", "NO"]
        app.launchArguments += arguments
        app.launch()
        return app
    }

    /// Any element type with this accessibility identifier (a Compose test tag surfaces on whichever element
    /// type the node maps to).
    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let a = XCTAttachment(screenshot: app.screenshot()); a.name = name; a.lifetime = .keepAlways; add(a)
    }

    private func attachTree(_ app: XCUIApplication, _ step: String) {
        let a = XCTAttachment(string: app.debugDescription); a.name = "tree-\(step)"; a.lifetime = .keepAlways; add(a)
    }

    /// The first download asks 'Do you want to proceed to download documents?' (the persecuted-country warning, same
    /// as Android). Taps the exact 'Yes' button (not 'Yes, do not ask again'); tolerates the dialog not appearing.
    private func confirmDownloadIfAsked(_ app: XCUIApplication) {
        let yes = app.buttons.matching(NSPredicate(format: "label == 'Yes'")).firstMatch
        if yes.waitForExistence(timeout: 15) {
            shot(app, "download-confirmation")
            yes.tap()
        }
    }

    /// In first-download mode the Download Documents screen is gated by an 'OK' button that enables once a Bible
    /// install has finished (same as Android: the user presses OK). Waits for it to exist AND be enabled, then taps.
    private func waitForEnabledOkAndTap(_ app: XCUIApplication, timeout: TimeInterval = 180) -> Bool {
        let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        let enabled = waitUntil(timeout: timeout) { ok.exists && ok.isEnabled }
        if enabled { ok.tap() }
        return enabled
    }

    /// Polls until `condition` holds or `timeout` elapses; returns the final result.
    private func waitUntil(timeout: TimeInterval, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            usleep(500_000)
        }
        return condition()
    }

    /// John 3 is shown in a web view: the chapter-opening 'Nicodemus' phrase label exists (WebKit splits the text
    /// into phrase-level StaticTexts).
    private func john3Shown(_ app: XCUIApplication, timeout: TimeInterval = 60) -> Bool {
        app.webViews.staticTexts.matching(NSPredicate(format: "label == 'Nicodemus'")).firstMatch
            .waitForExistence(timeout: timeout)
    }

    /// The distinct pane identifiers (`bible-webview-<windowId>`) among the WebView elements.
    private func paneIds(_ app: XCUIApplication) -> [String] {
        let ids = app.webViews.matching(NSPredicate(format: "identifier BEGINSWITH 'bible-webview-'"))
            .allElementsBoundByIndex.map(\.identifier)
        return Array(Set(ids)).sorted()
    }

    /// What a real user does before the app goes away: press Home so the app saves the workspace (it persists the
    /// passage when it resigns active / enters the background, like Android's onPause), then wait for the background
    /// state and give the asynchronous save a moment. XCTest terminates the app abruptly at the end of a test.
    private func sendToBackground(_ app: XCUIApplication) {
        XCUIDevice.shared.press(.home)
        _ = waitUntil(timeout: 15) { app.state != .runningForeground }
        sleep(3)
    }

    func testFreshInstallEasyStartReadsJohn3() {
        let app = launch(["-resetData", "-openRef", "John.3.16"])
        let welcome = element(app, "welcome-screen")
        XCTAssertTrue(welcome.waitForExistence(timeout: 30), "Welcome not shown on a fresh install")
        shot(app, "welcome")
        let easy = element(app, "welcome-easy-start")
        if !easy.waitForExistence(timeout: 10) { attachTree(app, "welcome-no-easy-start") }
        XCTAssertTrue(easy.exists, "welcome-easy-start not exposed as an accessibility identifier")
        easy.tap()
        confirmDownloadIfAsked(app)
        let okTapped = waitForEnabledOkAndTap(app)
        if !okTapped { attachTree(app, "no-enabled-ok") }
        XCTAssertTrue(okTapped, "Download Documents OK button not enabled within 180 s (no Bible installed?)")

        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 60), "reading host not shown after OK")
        // WebKit's accessibility tree splits the KJV text (Strong's markup) into phrase-level StaticTexts
        // ('For' 'God' 'so' 'loved' 'the world' ...), so no single label holds the verse. Assert John 3 renders
        // (chapter-opening 'Nicodemus') and that the phrase labels, joined in order, read John 3:16.
        let texts = app.webViews.staticTexts
        let found = john3Shown(app)
        var joined = ""
        if found {
            let labels = (0..<min(texts.count, 600)).map { texts.element(boundBy: $0).label }
            joined = labels.joined(separator: " ").split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
        }
        let hasVerse = joined.contains("For God so loved the world")
        if !hasVerse { attachTree(app, "no-john-3-16") }
        shot(app, "reading-john-3")
        XCTAssertTrue(found, "John 3 (Nicodemus) not rendered in the web view")
        XCTAssertTrue(hasVerse, "John 3:16 text not found in the web view's phrase labels: \(joined.prefix(300))")
        XCTAssertEqual(paneIds(app).count, 1, "a fresh install shows exactly one window, got \(paneIds(app))")
        sendToBackground(app) // lets the app persist the workspace (passage) before the test ends
    }

    func testIpadLaunchReachesWelcome() throws {
        try XCTSkipUnless(isPad, "iPad destination only")
        let app = launch(["-resetData"])
        let found = element(app, "welcome-screen").waitForExistence(timeout: 30)
        shot(app, "ipad-welcome")
        XCTAssertTrue(found, "Welcome not shown on iPad")
    }

    func testRelaunchReopensWorkspace() throws {
        // Relaunch without -resetData: the workspace left by the first test must open straight into reading.
        let app = launch([])
        if element(app, "welcome-screen").waitForExistence(timeout: 3) {
            // On iPhone Fresh runs before Relaunch, so Welcome here means the fresh-install test failed (or did not run).
            // On iPad the iPad test wiped the data first, so there is nothing to reopen.
            try XCTSkipIf(isPad, "iPad: data was wiped by testIpadLaunchReachesWelcome")
            XCTFail("Welcome shown on relaunch: the fresh-install test must pass first")
            return
        }
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 60), "reading host not shown on relaunch")
        XCTAssertFalse(element(app, "welcome-screen").exists, "Welcome shown on relaunch")
        // The workspace and passage survive: John 3 again, in the single window.
        XCTAssertTrue(john3Shown(app), "John 3 not shown after relaunch (workspace/passage lost)")
        XCTAssertEqual(paneIds(app).count, 1, "relaunch shows exactly one window, got \(paneIds(app))")
        app.terminate()
        let again = launch([])
        XCTAssertTrue(element(again, "reading-host").waitForExistence(timeout: 60), "reading host not shown on second relaunch")
        XCTAssertFalse(element(again, "welcome-screen").exists, "Welcome shown on second relaunch")
    }
}
