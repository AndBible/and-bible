import XCTest
import UIKit

/// Uses only Cancel on system UI; never sends a real report.
final class BugReportUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func app(_ extra: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-standardTestFixture", "-discrete_mode", "NO", "-show_calculator", "NO", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"] + extra
        app.launch()
        return app
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func assertShareAndClose(_ app: XCUIApplication, screenshot: String) {
        let activity = app.descendants(matching: .any).matching(identifier: "ActivityListView").firstMatch
        XCTAssertTrue(activity.waitForExistence(timeout: 15))
        let container = activity.descendants(matching: .any).matching(identifier: "ShareSheet.RemoteContainerView").firstMatch
        XCTAssertTrue(container.waitForExistence(timeout: 10))
        XCTAssertTrue(container.cells.matching(NSPredicate(format: "label == %@", "Copy")).firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(container.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "app_log.txt")).firstMatch.exists)
        shot(app, screenshot)
        if UIDevice.current.userInterfaceIdiom == .pad {
            // A dismissal region's center can hit an underlying settings row.
            // Always use the verified inert point outside the popover instead.
            do {
                // The observed iPad popover has no Close button. Tap its outside-dismiss region,
                // not Copy/Save or an underlying control. ActivityListView itself is full-screen.
                let copy = container.cells.matching(NSPredicate(format: "label == %@", "Copy")).firstMatch
                let attachment = container.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "app_log.txt")).firstMatch
                let point = CGPoint(x: app.frame.minX + app.frame.width * 0.02,
                                    y: app.frame.minY + app.frame.height * 0.10)
                let popover = app.popovers.firstMatch
                if popover.exists {
                    XCTAssertFalse(popover.frame.contains(point))
                } else {
                    // Header and action frames identify the observed right-center content, with
                    // a generous margin for its rounded container when UIKit omits AX popover bounds.
                    let contentBounds = copy.frame.union(attachment.frame).insetBy(dx: -64, dy: -64)
                    XCTAssertFalse(contentBounds.contains(point))
                    XCTAssertLessThan(point.x, contentBounds.minX)
                    XCTAssertLessThan(point.y, contentBounds.minY)
                }
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.02, dy: 0.10)).tap()
            }
        } else {
            let close = container.buttons["header.closeButton"].firstMatch
            XCTAssertTrue(close.exists)
            close.tap()
        }
        XCTAssertTrue(activity.waitForNonExistence(timeout: 10))
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
        assertShareAndClose(app, screenshot: "settings-bug-report-share")
        XCTAssertTrue(report.waitForExistence(timeout: 10))
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
        assertShareAndClose(app, screenshot: "recovery-bug-report-share")
        XCTAssertTrue(report.waitForExistence(timeout: 10))
    }
}
