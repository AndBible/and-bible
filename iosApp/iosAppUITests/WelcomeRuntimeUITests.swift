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
}
