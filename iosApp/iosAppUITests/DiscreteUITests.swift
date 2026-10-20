import XCTest

/// Real joined-app acceptance with device-local discrete_mode enabled by the simulator test runner.
/// Does not reset app data: after unlocking, either Welcome or the existing reading workspace is valid.
final class DiscreteUITests: XCTestCase {
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

    private func key(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func enter(_ app: XCUIApplication, _ labels: [String]) {
        for label in labels {
            let button = key(app, label)
            XCTAssertTrue(button.waitForExistence(timeout: 10), "Calculator key \(label) missing")
            button.tap()
        }
    }

    /// The unlock scenarios need discrete_mode=true persisted on the device by the controller before launch (the real
    /// device setting, not an argument-domain override); in an ordinary suite run they skip, like SyncAcceptanceUITests.
    private func requireControllerPreparedDiscreteMode() throws {
        guard ProcessInfo.processInfo.environment["TASK6_DISCRETE_ACCEPTANCE"] == "1" else {
            throw XCTSkip("Controller-only discrete acceptance is not enabled (TEST_RUNNER_TASK6_DISCRETE_ACCEPTANCE=1)")
        }
    }

    private func assertContentHidden(_ app: XCUIApplication) {
        XCTAssertFalse(element(app, "reading-host").exists, "Reading content visible before PIN unlock")
        XCTAssertFalse(element(app, "welcome-screen").exists, "Welcome visible before PIN unlock")
    }

    /// Controller-only fixture preparation; inspect the Home attachment before the strict icon scenario.
    func testPreparePrimaryIconForController() {
        let app = XCUIApplication()
        app.launchArguments = ["-task6ResetCrashState", "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let disclosureText = NSPredicate(format: "label CONTAINS %@ AND label CONTAINS %@",
                                         "You have changed the icon for", "AndBible Dev")
        let appAlert = app.alerts.containing(disclosureText).firstMatch
        let systemAlert = springboard.alerts.containing(disclosureText).firstMatch
        let deadline = Date().addingTimeInterval(90)
        var readyAt: Date?
        var disclosureCount = 0
        // Ready can precede the asynchronous icon request and its bounded retries; keep observing both owners.
        while Date() < deadline {
            if appAlert.exists || systemAlert.exists {
                let owner = appAlert.exists ? app : springboard
                let alert = appAlert.exists ? appAlert : systemAlert
                disclosureCount += 1
                shot(owner, "primary-preparation-disclosure-\(disclosureCount)")
                XCTAssertTrue(alert.buttons["OK"].waitForExistence(timeout: 5))
                alert.buttons["OK"].tap()
                let dismissed = NSPredicate { _, _ in !appAlert.exists && !systemAlert.exists }
                XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: dismissed, object: app)], timeout: 10),
                               .completed, "Preparation disclosure did not dismiss")
                readyAt = nil
            }
            let ready = element(app, "reading-host").exists || element(app, "welcome-screen").exists
            if ready && readyAt == nil { readyAt = Date() }
            if let readyAt, ready && Date().timeIntervalSince(readyAt) >= 20 { break }
            usleep(200_000)
        }
        XCTAssertNotNil(readyAt, "Preparation never reached Welcome or reading")
        if let readyAt {
            XCTAssertGreaterThanOrEqual(Date().timeIntervalSince(readyAt), 20, "Preparation exhausted its settling deadline")
        }
        XCTAssertTrue(element(app, "reading-host").exists || element(app, "welcome-screen").exists)
        XCTAssertFalse(appAlert.exists || systemAlert.exists, "Preparation disclosure still visible")
        shot(app, "primary-preparation-ready")
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10))
        XCTAssertTrue(springboard.wait(for: .runningForeground, timeout: 10))
        let homeSettled = NSPredicate { _, _ in
            springboard.icons["AndBible Dev"].firstMatch.isHittable && !appAlert.exists && !systemAlert.exists
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: homeSettled, object: springboard)], timeout: 10),
                       .completed, "Preparation Home screen did not settle")
        shot(springboard, "primary-preparation-home-inspect-icon")
        app.terminate()
    }

    /// Controller starts from the primary icon and persists discrete_mode=true before this scenario.
    /// Accept the OS-owned disclosure (C6), then retain a SpringBoard image for visual icon inspection.
    func testDiscreteIconAfterUnlock() throws {
        try requireControllerPreparedDiscreteMode()
        let app = XCUIApplication()
        // Controller also uses an English simulator for the SpringBoard-owned system disclosure.
        app.launchArguments += ["-calculator_pin", "1234", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        XCTAssertTrue(key(app, "=").waitForExistence(timeout: 30))
        assertContentHidden(app)
        enter(app, ["1", "2", "3", "4", "="])

        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        // The OS disclosure is app-owned on iOS 18 and SpringBoard-owned on iOS 26.
        let disclosureText = NSPredicate(format: "label CONTAINS %@ AND label CONTAINS %@",
                                         "You have changed the icon for", "AndBible Dev")
        let appAlert = app.alerts.containing(disclosureText).firstMatch
        let systemAlert = springboard.alerts.containing(disclosureText).firstMatch
        let disclosure = NSPredicate { _, _ in appAlert.exists || systemAlert.exists }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: disclosure, object: app)], timeout: 60),
                       .completed, "Expected the identified system alternate-icon disclosure in either owner")
        let owner = appAlert.exists ? app : springboard
        let alert = appAlert.exists ? appAlert : systemAlert
        shot(owner, "calculator-icon-system-disclosure")
        alert.buttons["OK"].tap()
        let dismissed = NSPredicate { _, _ in !appAlert.exists && !systemAlert.exists }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: dismissed, object: app)], timeout: 10), .completed)
        let ready = NSPredicate { _, _ in
            self.element(app, "reading-host").exists || self.element(app, "welcome-screen").exists
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: ready, object: app)], timeout: 60), .completed)
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(springboard.wait(for: .runningForeground, timeout: 10))
        let homeSettled = NSPredicate { _, _ in
            springboard.icons["AndBible Dev"].firstMatch.isHittable && !appAlert.exists && !systemAlert.exists
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: homeSettled, object: springboard)], timeout: 10), .completed)
        shot(springboard, "home-calculator-icon")
    }

    func testCalculatorGateUnlocksWithPin() throws {
        try requireControllerPreparedDiscreteMode()
        let app = XCUIApplication()
        // NSUserDefaults argument-domain override keeps the PIN deterministic without mutating stored preferences.
        // discrete_mode is deliberately persisted by the controller before launch, exercising the real device setting.
        app.launchArguments += ["-calculator_pin", "1234"]
        app.launch()

        XCTAssertTrue(key(app, "=").waitForExistence(timeout: 30), "Calculator must appear before app startup")
        assertContentHidden(app)
        shot(app, "calculator-gate")

        enter(app, ["9", "9", "9", "9", "="])
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label == '9999'")).firstMatch.exists,
                      "Wrong PIN should remain displayed on the calculator")
        XCTAssertTrue(key(app, "C").exists, "Wrong PIN dismissed the calculator")
        assertContentHidden(app)
        shot(app, "calculator-wrong-pin")

        enter(app, ["C", "7", "+", "8", "="])
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label == '15'")).firstMatch.exists,
                      "Calculator arithmetic did not display 7 + 8 = 15")
        assertContentHidden(app)
        shot(app, "calculator-arithmetic")

        enter(app, ["C", "1", "2", "3", "4", "="])
        let ready = NSPredicate { _, _ in
            self.element(app, "reading-host").exists || self.element(app, "welcome-screen").exists
        }
        let shown = XCTNSPredicateExpectation(predicate: ready, object: app)
        XCTAssertEqual(XCTWaiter.wait(for: [shown], timeout: 60), .completed,
                       "PIN 1234 did not reach the real app (Welcome or reading)")
        XCTAssertFalse(key(app, "=").exists, "Calculator still shown after successful PIN")
        shot(app, "calculator-unlocked-real-app")
    }
}
