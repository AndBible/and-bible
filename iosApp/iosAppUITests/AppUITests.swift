import XCTest

/// Real-app flows (I2 phase 2 join): fresh install -> Welcome -> Easy start download -> reading; relaunch; iPad.
/// Uses the debug-only launch arguments `-resetData` (fresh install) and `-openRef <osisRef>` (see
/// `IosDebugLaunchArgs.kt`). The tests run in definition order within the class (XCTest sorts alphabetically:
/// testFreshInstall..., testIpad..., testRelaunch...), and `testRelaunchReopensWorkspace` needs the data the first
/// one left; it skips when there is none.
final class AppUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

    private func launch(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
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

    func testFreshInstallEasyStartReadsJohn3() {
        let app = launch(["-resetData", "-openRef", "John.3.16"])
        let welcome = element(app, "welcome-screen")
        XCTAssertTrue(welcome.waitForExistence(timeout: 30), "Welcome not shown on a fresh install")
        shot(app, "welcome")
        let easy = element(app, "welcome-easy-start")
        if !easy.waitForExistence(timeout: 10) { attachTree(app, "welcome-no-easy-start") }
        XCTAssertTrue(easy.exists, "welcome-easy-start not exposed as an accessibility identifier")
        easy.tap()

        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 180), "reading host not shown within 180 s")
        let verse = app.webViews.staticTexts
            .containing(NSPredicate(format: "label CONTAINS 'For God so loved the world'")).firstMatch
        let found = verse.waitForExistence(timeout: 60)
        if !found { attachTree(app, "no-john-3-16") }
        shot(app, "reading-john-3")
        XCTAssertTrue(found, "John 3:16 text not visible in the web view")
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
            throw XCTSkip("no installed data (run testFreshInstallEasyStartReadsJohn3 first)")
        }
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 60), "reading host not shown on relaunch")
        XCTAssertFalse(element(app, "welcome-screen").exists, "Welcome shown on relaunch")
        app.terminate()
        let again = launch([])
        XCTAssertTrue(element(again, "reading-host").waitForExistence(timeout: 60), "reading host not shown on second relaunch")
        XCTAssertFalse(element(again, "welcome-screen").exists, "Welcome shown on second relaunch")
    }
}
