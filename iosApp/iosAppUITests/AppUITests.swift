import XCTest

/// Real-app flows (I2 phase 2 join): fresh install -> Welcome -> Easy start download -> reading; relaunch; iPad.
/// Uses explicit DEBUG-only device settings and finite real-feature routes. Reset deletes app data,
/// not UserDefaults. Each lifecycle test provisions its own recommended bundle and chooses KJV through the UI.
class RealAppUITestCase: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

    func launch(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += arguments
        app.launch()
        return app
    }

    /// Any element type with this accessibility identifier (a Compose test tag surfaces on whichever element
    /// type the node maps to).
    func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    func shot(_ app: XCUIApplication, _ name: String) {
        let a = XCTAttachment(screenshot: app.screenshot()); a.name = name; a.lifetime = .keepAlways; add(a)
    }

    func attachTree(_ app: XCUIApplication, _ step: String) {
        let a = XCTAttachment(string: app.debugDescription); a.name = "tree-\(step)"; a.lifetime = .keepAlways; add(a)
    }

    /// The first download asks 'Do you want to proceed to download documents?' (the persecuted-country warning, same
    /// as Android). Taps the exact 'Yes' button (not 'Yes, do not ask again'); tolerates the dialog not appearing.
    func confirmDownloadIfAsked(_ app: XCUIApplication) {
        let yes = app.buttons.matching(NSPredicate(format: "label == 'Yes'")).firstMatch
        if yes.waitForExistence(timeout: 15) {
            shot(app, "download-confirmation")
            yes.tap()
        }
    }

    /// In first-download mode the Download Documents screen is gated by an 'OK' button that enables once a Bible
    /// install has finished (same as Android: the user presses OK). Waits for it to exist AND be enabled, then taps.
    func waitForEnabledOkAndTap(_ app: XCUIApplication, timeout: TimeInterval = 180) -> Bool {
        let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        let enabled = waitUntil(timeout: timeout) { ok.exists && ok.isEnabled }
        if enabled { ok.tap() }
        return enabled
    }

    /// Polls until `condition` holds or `timeout` elapses; returns the final result.
    func waitUntil(timeout: TimeInterval, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            usleep(500_000)
        }
        return condition()
    }

    /// John 3 is shown in a web view: the chapter-opening 'Nicodemus' phrase label exists (WebKit splits the text
    /// into phrase-level StaticTexts).
    func john3Shown(_ app: XCUIApplication, timeout: TimeInterval = 60) -> Bool {
        app.webViews.staticTexts.matching(NSPredicate(format: "label == 'Nicodemus'")).firstMatch
            .waitForExistence(timeout: timeout)
    }

    /// The distinct pane identifiers (`bible-webview-<windowId>`) among the WebView elements.
    func paneIds(_ app: XCUIApplication) -> [String] {
        let ids = app.webViews.matching(NSPredicate(format: "identifier BEGINSWITH 'bible-webview-'"))
            .allElementsBoundByIndex.map(\.identifier)
        return Array(Set(ids)).sorted()
    }

    /// What a real user does before the app goes away: press Home so the app saves the workspace (it persists the
    /// passage when it resigns active / enters the background, like Android's onPause), then wait for the background
    /// state and give the asynchronous save a moment. XCTest terminates the app abruptly at the end of a test.
    func sendToBackground(_ app: XCUIApplication) {
        XCUIDevice.shared.press(.home)
        _ = waitUntil(timeout: 15) { app.state != .runningForeground }
        sleep(3)
    }

    func freshReading(scenario: String = "chooser") -> XCUIApplication {
        let app = launch(["-resetData", "-standardTestFixture", "-openRef", "John.3.16", "-featureScenario", scenario])
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

        let ready = scenario == "chooser" ? "debug-open-chooser" : "debug-feature-ready"
        XCTAssertTrue(element(app, ready).waitForExistence(timeout: 60), "Normal reading initialization must finish before feature navigation")
        return app
    }

    func selectKJV(_ app: XCUIApplication) {
        let kjv = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'KJV '")).firstMatch
        if !kjv.waitForExistence(timeout: 30) { attachTree(app, "chooser-no-kjv") }
        XCTAssertTrue(kjv.exists, "KJV must be selected through the real document chooser")
        kjv.tap()
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 30))
    }

    func assertExactJohn316(_ app: XCUIApplication) {
        // WebKit's accessibility tree splits the KJV text (Strong's markup) into phrase-level StaticTexts
        // ('For' 'God' 'so' 'loved' 'the world' ...), so no single label holds the verse. Assert John 3 renders
        // (chapter-opening 'Nicodemus') and that the phrase labels, joined in order, read John 3:16.
        let texts = app.webViews.staticTexts
        let found = john3Shown(app)
        var joined = ""
        let hasVerse = waitUntil(timeout: 60) {
            let labels = (0..<min(texts.count, 600)).map { texts.element(boundBy: $0).label }
            joined = labels.joined(separator: " ").split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
            return joined.contains("For God so loved the world")
        }
        if !hasVerse { attachTree(app, "no-john-3-16") }
        shot(app, "reading-john-3")
        XCTAssertTrue(found, "John 3 (Nicodemus) not rendered in the web view")
        XCTAssertTrue(hasVerse, "John 3:16 text not found in the web view's phrase labels: \(joined.prefix(300))")
        XCTAssertEqual(paneIds(app).count, 1, "a fresh install shows exactly one window, got \(paneIds(app))")
    }
}

final class AppUITests: RealAppUITestCase {
    func testFreshInstallEasyStartReadsJohn3() {
        let app = freshReading()
        selectKJV(app)
        assertExactJohn316(app)
        sendToBackground(app)
    }

    func testIpadLaunchReachesWelcome() throws {
        try XCTSkipUnless(isPad, "iPad destination only")
        let app = launch(["-resetData", "-standardTestFixture"])
        let found = element(app, "welcome-screen").waitForExistence(timeout: 30)
        shot(app, "ipad-welcome")
        XCTAssertTrue(found, "Welcome not shown on iPad")
    }

    func testRelaunchReopensWorkspace() throws {
        // Self-contained on either device; no dependency on test ordering or another test's data.
        let fresh = freshReading()
        selectKJV(fresh)
        assertExactJohn316(fresh)
        sendToBackground(fresh)
        fresh.terminate()
        let app = launch([])
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 60), "reading host not shown on relaunch")
        XCTAssertFalse(element(app, "welcome-screen").exists, "Welcome shown on relaunch")
        // The workspace and passage survive: John 3 again, in the single window.
        assertExactJohn316(app)
        XCTAssertEqual(paneIds(app).count, 1, "relaunch shows exactly one window, got \(paneIds(app))")
        app.terminate()
        let again = launch([])
        XCTAssertTrue(element(again, "reading-host").waitForExistence(timeout: 60), "reading host not shown on second relaunch")
        XCTAssertFalse(element(again, "welcome-screen").exists, "Welcome shown on second relaunch")
    }
}
