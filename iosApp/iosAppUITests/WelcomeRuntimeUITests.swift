import XCTest

/// Real fresh Welcome entry points. File installation and external-link delivery remain stream F work.
final class WelcomeRuntimeUITests: RealAppUITestCase {
    private func require(_ condition: Bool, _ app: XCUIApplication, _ step: String,
                         _ message: String, file: StaticString = #filePath, line: UInt = #line) {
        if !condition {
            shot(app, "welcome-\(step)-failure")
            attachTree(app, "welcome-\(step)-failure")
        }
        XCTAssertTrue(condition, message, file: file, line: line)
    }

    private func button(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    /// Welcome rows merge their supporting text; activate the actual Button parent, not its title child.
    private func row(_ app: XCUIApplication, _ title: String) -> XCUIElement {
        app.buttons.containing(.staticText, identifier: title).firstMatch
    }

    private func requireRow(_ app: XCUIApplication, _ title: String) -> XCUIElement {
        let target = row(app, title)
        let found = waitUntil(timeout: 15) { target.exists && target.isHittable }
        require(found, app, title, "Actual Welcome row Button unavailable: \(title)")
        return target
    }

    private func advancedWelcome() -> XCUIApplication {
        let app = launch(["-resetData", "-standardTestFixture"])
        require(element(app, "welcome-screen").waitForExistence(timeout: 30), app,
                "fresh", "Fresh standard launch must show actual Welcome")
        require(element(app, "welcome-easy-start").exists, app,
                "easy", "Fresh Welcome must initially show Easy content")
        let advanced = button(app, "Advanced")
        require(advanced.waitForExistence(timeout: 10) && advanced.isHittable, app,
                "advanced-tab", "Actual Advanced tab unavailable")
        advanced.tap()
        _ = requireRow(app, "Download Documents")
        _ = requireRow(app, "Load Documents From Files")
        _ = requireRow(app, "Restore Database File")
        require(!element(app, "welcome-easy-start").exists, app,
                "advanced-content", "Advanced must replace Easy content")
        require(button(app, "Website").exists && button(app, "GitHub").exists, app,
                "standard-footer", "Standard Welcome must render both real footer links")
        shot(app, "welcome-advanced")
        attachTree(app, "welcome-advanced")
        return app
    }

    func testAdvancedDownloadEntryAndConsent() {
        let app = advancedWelcome()
        requireRow(app, "Download Documents").tap()
        let consent = app.staticTexts.matching(NSPredicate(
            format: "label == 'Do you want to proceed to download documents?'"
        )).firstMatch
        require(consent.waitForExistence(timeout: 30), app,
                "download-consent", "Fresh Advanced download must reach the actual consent dialog")
        let yes = button(app, "Yes")
        require(yes.exists && yes.isHittable, app, "download-yes", "Actual consent Yes unavailable")
        shot(app, "welcome-download-consent")
        yes.tap()
        let language = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Language'")).firstMatch
        let catalogue = waitUntil(timeout: 60) {
            !consent.exists && language.exists && language.isHittable
        }
        require(catalogue, app, "download-catalogue", "Consent must lead to actual Download language filter")
        require(!element(app, "welcome-screen").exists, app,
                "download-left-welcome", "Download must leave Welcome")
        shot(app, "welcome-download-catalogue")
        attachTree(app, "welcome-download-catalogue")
        // No document is installed here; provisioning/reading has separate lifecycle coverage.
    }

    func testAdvancedImportPreludeAndNativePickerCancellation() {
        let app = advancedWelcome()
        requireRow(app, "Load Documents From Files").tap()
        let prelude = app.staticTexts.matching(NSPredicate(
            format: "label == 'Load Documents From Files'"
        )).firstMatch
        let proceed = button(app, "Proceed")
        let preludeOpened = waitUntil(timeout: 30) {
            prelude.exists && proceed.isHittable && self.button(app, "Cancel").isHittable
        }
        require(preludeOpened, app,
                "import-prelude", "Actual import modal must expose title plus Proceed/Cancel, not just underlying Welcome row")
        require(proceed.exists && proceed.isHittable, app,
                "import-proceed", "Real import prelude must expose Proceed")
        shot(app, "welcome-import-prelude")
        attachTree(app, "welcome-import-prelude")
        proceed.tap()
        // Stream F's real UIDocumentPicker opens (written while it was a pending stub that returned empty): cancel
        // it. This observes route cancellation, NOT installation.
        let cancel = button(app, "Cancel")
        let pickerShown = waitUntil(timeout: 20) {
            !proceed.exists && app.navigationBars.firstMatch.exists && cancel.exists && cancel.isHittable
        }
        require(pickerShown, app, "import-native-picker", "Proceed must open the actual UIKit Files picker with Cancel")
        shot(app, "welcome-import-native-picker")
        cancel.tap()
        require(waitUntil(timeout: 30) {
            !app.navigationBars.firstMatch.exists && self.row(app, "Restore Database File").isHittable
        }, app, "import-cancelled", "Picker cancellation must return from import without claiming success")
        require(element(app, "welcome-screen").exists, app,
                "import-return", "Cancelled import must retain Welcome")
        require(!element(app, "reading-host").exists, app,
                "import-no-reading", "Cancelled import must not report an installed document")
        shot(app, "welcome-import-cancelled")
        attachTree(app, "welcome-import-cancelled")
    }

    func testAdvancedRestoreNativePickerCancellation() {
        let app = advancedWelcome()
        requireRow(app, "Restore Database File").tap()
        let cancel = button(app, "Cancel")
        let pickerShown = waitUntil(timeout: 20) {
            app.navigationBars.firstMatch.exists && cancel.exists && cancel.isHittable
        }
        require(pickerShown, app, "restore-native-picker",
                "Actual UIKit Files picker navigation and Cancel must appear; unknown AX contract needs diagnosis")
        shot(app, "welcome-restore-native-picker")
        attachTree(app, "welcome-restore-native-picker")
        cancel.tap()
        require(waitUntil(timeout: 20) {
            !app.navigationBars.firstMatch.exists && self.row(app, "Restore Database File").isHittable
        }, app, "restore-cancel", "Native picker cancellation must return to actual Advanced Welcome")
        require(element(app, "welcome-screen").exists, app,
                "restore-still-welcome", "Cancelled restore must not enter reading or a restored workspace")
        require(!element(app, "reading-host").exists, app,
                "restore-no-reading", "Cancelled restore must not report a completed restore")
        shot(app, "welcome-restore-cancelled")
        attachTree(app, "welcome-restore-cancelled")
    }
}
