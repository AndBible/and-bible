import XCTest

/// Uses only Cancel on system UI; never sends a real report.
final class BugReportUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func app(_ extra: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-discrete_mode", "NO", "-show_calculator", "NO", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"] + extra
        app.launch()
        return app
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testSettingsReportRequiresConfirmationThenOpensShare() {
        let app = app(["-task5Settings"])
        let report = app.buttons["Feedback / bug report"].firstMatch
        XCTAssertTrue(report.waitForExistence(timeout: 40))
        report.tap()
        let confirm = app.alerts["Send bug report via email"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: 10))
        confirm.buttons["Cancel"].tap()
        report.tap()
        XCTAssertTrue(confirm.waitForExistence(timeout: 10))
        confirm.buttons["OK"].tap()
        // Mail is not configured on the acceptance simulator. System owns this UI, not Compose.
        XCTAssertTrue(app.buttons["Copy"].firstMatch.waitForExistence(timeout: 15))
        shot(app, "settings-bug-report-share")
        // Close the share sheet without selecting a destination or sending mail.
        let close = app.buttons["Close"].firstMatch
        if close.exists { close.tap() } else { app.swipeDown() }
    }

    func testErrorDialogBackupOpensActualBackupRoute() {
        let app = app(["-task5Settings", "-task5Error"])
        let backup = app.buttons["Backup & Restore"].firstMatch
        XCTAssertTrue(backup.waitForExistence(timeout: 40))
        shot(app, "error-report-options")
        backup.tap()
        XCTAssertTrue(app.staticTexts["Backup & Restore"].firstMatch.waitForExistence(timeout: 20))
        shot(app, "error-report-backup")
    }

    func testRecoveryReportBeforeDatabaseStartup() {
        let app = app(["-seedCrashRecovery"])
        let report = app.descendants(matching: .any).matching(identifier: "crash-report").firstMatch
        XCTAssertTrue(report.waitForExistence(timeout: 20))
        report.tap()
        let confirm = app.alerts["Send bug report via email"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: 10))
        confirm.buttons["OK"].tap()
        XCTAssertTrue(app.buttons["Copy"].firstMatch.waitForExistence(timeout: 15))
        shot(app, "recovery-bug-report-share")
    }
}
