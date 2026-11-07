import XCTest

/// Exercises the production incoming SWORD installation and the system permission alert.
final class NotificationsUITests: RealAppUITestCase {
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    private static var kjvEnsured = false

    override func setUp() {
        super.setUp()
        if !Self.kjvEnsured { ensureKJVInstalled(); Self.kjvEnsured = true }
        XCUIApplication().resetAuthorizationStatus(for: .userNotifications)
    }

    override func tearDown() {
        XCUIApplication().terminate()
        XCUIApplication().resetAuthorizationStatus(for: .userNotifications)
        super.tearDown()
    }

    func testFirstConfirmedInstallRequestsPermission() {
        let app = launch(["-standardTestFixture", "-resetDatabases", "-openDocument", "KJV",
                          "-openRef", "John.3.16", "-incomingRuntimeTest", "-incomingFixture", "sword",
                          "-incomingRemoveFixtures", "-installCommitDelayMs", "1500",
                          "-AppleLanguages", "(en)", "-AppleLocale", "en_US"])
        let confirm = app.staticTexts["Do you want to install module (f-rawtext.zip)?"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: 60))
        let permission = springboard.alerts.containing(NSPredicate(format: "label CONTAINS[c] 'notifications'")).firstMatch
        XCTAssertFalse(permission.exists, "The install confirmation must not itself request authorization")
        app.buttons["OK"].firstMatch.tap()
        XCTAssertTrue(permission.waitForExistence(timeout: 30), "The confirmed user-started installation requests permission")
        shot(springboard, "notification-permission")
        permission.buttons["Allow"].tap()
    }
}
