import XCTest

/// Posts through the shared event owner before Koin or the app dialog host exists.
final class UserMessagesUITests: XCTestCase {
    /// Activity menu off: the dialog path stays as it was.
    func testStartupUserMessageAppearsInTheAppDialog() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-standardTestFixture", "-task6ResetCrashState",
                               "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
                               "-task5Settings", "-startupUserMessage", "-activityMenuOff"]
        app.launch()
        let message = app.staticTexts["Test error (debug build)"].firstMatch
        XCTAssertTrue(message.waitForExistence(timeout: 60), "Startup UserMessages must not be dropped")
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "startup-user-message"
        shot.lifetime = .keepAlways
        add(shot)
        let ok = app.buttons["OK"].firstMatch
        XCTAssertTrue(ok.waitForExistence(timeout: 5))
        ok.tap()
        XCTAssertTrue(message.waitForNonExistence(timeout: 10))
        app.terminate()
    }

    /// Activity menu on (the iOS default): the error becomes a toast pointing at the menu, with no OK dialog.
    func testStartupUserMessageBecomesAToastWhenTheActivityMenuIsOn() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-standardTestFixture", "-task6ResetCrashState",
                               "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
                               "-startupUserMessage"]
        app.launch()
        let toast = app.staticTexts["Test error (debug build) \u{2014} see menu"].firstMatch
        XCTAssertTrue(toast.waitForExistence(timeout: 60), "The rerouted error must show as a toast")
        XCTAssertFalse(app.buttons["OK"].firstMatch.exists, "No modal dialog when the error goes to the activity list")
        let badge = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "1 failed")).firstMatch
        XCTAssertTrue(badge.waitForExistence(timeout: 10), "The overflow button must carry the unread-failure badge")
        attach(app, "activity-toast-and-badge")
        let more = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "More options")).firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let row = app.staticTexts["Test error (debug build)"].firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 10), "The failure must be a row in the overflow menu")
        attach(app, "activity-menu-row")
        XCTAssertTrue(badge.waitForNonExistence(timeout: 10), "Opening the menu marks the failure read")
        app.terminate()
    }

    private func attach(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }
}
