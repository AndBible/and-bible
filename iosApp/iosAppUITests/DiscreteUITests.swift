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

    private func assertContentHidden(_ app: XCUIApplication) {
        XCTAssertFalse(element(app, "reading-host").exists, "Reading content visible before PIN unlock")
        XCTAssertFalse(element(app, "welcome-screen").exists, "Welcome visible before PIN unlock")
    }

    func testCalculatorGateUnlocksWithPin() {
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
