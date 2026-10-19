import XCTest

/// Run on a disposable simulator: -resetData deletes app files, -seedCrashRecovery seeds the real preferences.
final class CrashRecoveryUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testSeededRecoveryOpensRealUninitialisedBackup() {
        let app = XCUIApplication()
        app.launchArguments = ["-resetData", "-seedCrashRecovery", "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        XCTAssertTrue(element(app, "crash-recovery").waitForExistence(timeout: 30))
        XCTAssertFalse(element(app, "reading-host").exists)
        XCTAssertFalse(element(app, "welcome-screen").exists)
        shot(app, "crash-recovery")
        element(app, "crash-backup").tap()
        XCTAssertTrue(element(app, "recovery-backup-uninitialised").waitForExistence(timeout: 30),
                      "Real Backup route must report databases still closed")
        XCTAssertTrue(app.staticTexts["Backup & Restore"].firstMatch.waitForExistence(timeout: 15))
        XCTAssertFalse(element(app, "reading-host").exists)
        XCTAssertFalse(element(app, "welcome-screen").exists)
        shot(app, "crash-recovery-backup")
        app.buttons["Back"].firstMatch.tap()
        XCTAssertTrue(element(app, "crash-recovery").waitForExistence(timeout: 15))
        XCTAssertFalse(element(app, "reading-host").exists)
        XCTAssertFalse(element(app, "welcome-screen").exists)
    }
}
