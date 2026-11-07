import XCTest

/// Run the permission tests on separately freshly installed apps: XCTest cannot reset notification authorization.
final class NotificationsUITests: RealAppUITestCase {
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    private let message = "Test error (debug build)"
    private static var kjvEnsured = false
    private let english = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
    private var provisioning = false

    override func launch(_ arguments: [String]) -> XCUIApplication {
        super.launch(arguments + (provisioning ? ["-notificationReset", "-notificationSettingOff"] : []))
    }

    override func setUp() {
        super.setUp()
        if !Self.kjvEnsured {
            provisioning = true
            ensureKJVInstalled()
            provisioning = false
            Self.kjvEnsured = true
        }
    }

    override func tearDown() {
        XCUIApplication().terminate()
        // Restore the app preference and asked flag for subsequent suites, without requesting permission.
        let cleanup = launch(["-standardTestFixture", "-notificationReset"] + english)
        cleanup.terminate()
        super.tearDown()
    }

    private func install(_ extra: [String] = []) -> XCUIApplication {
        let app = launch(["-standardTestFixture", "-resetDatabases", "-openDocument", "KJV",
                          "-openRef", "John.3.16", "-incomingRuntimeTest", "-incomingFixture", "sword",
                          "-incomingRemoveFixtures", "-installCommitDelayMs", "1500", "-notificationReset"] + extra + english)
        XCTAssertTrue(app.staticTexts["Do you want to install module (f-rawtext.zip)?"].firstMatch.waitForExistence(timeout: 60))
        return app
    }

    private var permission: XCUIElement {
        springboard.alerts.containing(NSPredicate(format: "label CONTAINS[c] 'notifications'")).firstMatch
    }

    func test02FirstConfirmedInstallRequestsPermission() {
        let app = install()
        XCTAssertFalse(permission.exists, "The install confirmation must not itself request authorization")
        app.buttons["OK"].firstMatch.tap()
        XCTAssertTrue(permission.waitForExistence(timeout: 30), "A confirmed user-started installation requests permission")
        shot(springboard, "notification-permission")
        permission.buttons["Allow"].tap()
    }

    func test01SettingOffBeforeFirstInstallDoesNotRequestPermission() {
        let app = install(["-notificationSettingOff"])
        app.buttons["OK"].firstMatch.tap()
        let progress = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS '[commit '")).firstMatch
        XCTAssertTrue(progress.waitForExistence(timeout: 60), "The actual installation must start")
        XCTAssertFalse(permission.waitForExistence(timeout: 5), "Disabled notifications must not request permission")
        shot(app, "install-notifications-disabled")
    }

    // Run separately after uninstalling the app; denial is a real system authorization decision.
    func testDeniedPermissionShowsSettingsLink() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["NOTIFICATION_DENIAL_TEST"] == "1",
                          "Run separately with a fresh app installation and NOTIFICATION_DENIAL_TEST=1")
        let app = install()
        app.buttons["OK"].firstMatch.tap()
        XCTAssertTrue(permission.waitForExistence(timeout: 30))
        permission.buttons["Don't Allow"].tap()
        app.terminate()
        let settings = launch(["-standardTestFixture", "-task5Settings"] + english)
        XCTAssertTrue(waitForSettingsRoot(settings))
        _ = revealSettingsRow(settings, "system_notifications")
        XCTAssertTrue(settings.staticTexts["Turned off in iOS Settings"].firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(settings.staticTexts["Open settings"].firstMatch.exists)
        shot(settings, "notification-denied-settings")
    }

    private func failureApp() -> XCUIApplication {
        launch(["-standardTestFixture", "-notificationReset", "-notificationDebugControls"] + english)
    }

    private func trigger(_ app: XCUIApplication, _ id: String) {
        let button = element(app, id)
        XCTAssertTrue(button.waitForExistence(timeout: 60), "Debug failure control is ready")
        button.tap()
    }

    private var notification: XCUIElement {
        springboard.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", message)).firstMatch
    }

    func testAutomaticFailureInForegroundShowsSystemBanner() {
        let app = failureApp()
        trigger(app, "notification-debug-automatic")
        XCTAssertTrue(notification.waitForExistence(timeout: 30), "Automatic failure must reach SpringBoard")
        shot(springboard, "automatic-failure-banner")
    }

    func testUserFailureInForegroundShowsDialogWithoutBanner() {
        let app = failureApp()
        trigger(app, "notification-debug-user")
        let dialog = app.buttons["OK"].firstMatch
        XCTAssertTrue(dialog.waitForExistence(timeout: 30), "User failure must retain the dialog")
        XCTAssertFalse(notification.waitForExistence(timeout: 5), "No duplicate system banner")
        shot(app, "user-failure-dialog")
        dialog.tap()
    }

    func testBackgroundFailureNotificationOpensActivityListAfterColdLaunch() {
        let app = failureApp()
        trigger(app, "notification-debug-background")
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(notification.waitForExistence(timeout: 30), "Background failure must reach SpringBoard")
        shot(springboard, "background-failure-notification")
        app.terminate()
        XCTAssertTrue(notification.exists, "Delivered notification survives termination")
        notification.tap()
        let activity = app.staticTexts["Background activity"].firstMatch
        XCTAssertTrue(activity.waitForExistence(timeout: 60), "Notification activation opens the activity list")
        shot(app, "notification-cold-launch-activity")
    }
}
