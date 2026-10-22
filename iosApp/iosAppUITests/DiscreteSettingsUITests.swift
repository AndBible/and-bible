import XCTest

/// H2 Task 9 (W8): the discrete-mode toggle and the calculator PIN, driven entirely through Settings so the class runs
/// by default (no controller-persisted state, unlike DiscreteUITests).
///
/// Preconditions are established here: the first launch passes `-standardTestFixture` (writes discrete_mode and
/// show_calculator off, locale en) and the PIN is set to 1234 through the Settings editor. Later launches must NOT pass
/// `-discrete_mode`, `-show_calculator`, `-calculator_pin` or `-standardTestFixture`: those would shadow or reset the
/// very settings under test. A teardown block restores discrete off and PIN 1234 even after a failure.
final class DiscreteSettingsUITests: RealAppUITestCase {
    private let english = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]

    private func launchSettings(fixture: Bool) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-task6ResetCrashState", "-task5Settings"] + english
        if fixture { app.launchArguments += ["-standardTestFixture"] }
        app.launch()
        return app
    }

    private func key(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func enter(_ app: XCUIApplication, _ labels: [String]) {
        for label in labels {
            let button = key(app, label)
            harnessRequire(button.waitForExistence(timeout: 10), app, "Calculator key \(label) missing")
            button.tap()
        }
    }

    private func relaunch(_ app: XCUIApplication) -> XCUIApplication {
        XCUIDevice.shared.press(.home)
        usleep(1_000_000)
        app.terminate()
        return launchSettings(fixture: false)
    }

    private func discreteSwitch(_ app: XCUIApplication) -> XCUIElement { revealSettingsRow(app, "discrete_mode") }

    /// The OS alternate-icon disclosure is owned by the app (iOS 18) or SpringBoard (iOS 26); accept it explicitly.
    /// `required` false tolerates its absence (the OS can skip it) but records that in an attachment.
    private func acknowledgeIconAlert(_ app: XCUIApplication, required: Bool, _ step: String) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let text = NSPredicate(format: "label CONTAINS %@", "You have changed the icon for")
        let appAlert = app.alerts.containing(text).firstMatch
        let systemAlert = springboard.alerts.containing(text).firstMatch
        let appeared = waitUntil(timeout: required ? 60 : 20) { appAlert.exists || systemAlert.exists }
        if !appeared {
            if required { harnessRequire(false, app, "\(step): the icon-change alert never appeared") }
            else { shot(app, "\(step)-no-icon-alert") }
            return
        }
        let owner = appAlert.exists ? app : springboard
        let alert = appAlert.exists ? appAlert : systemAlert
        shot(owner, "\(step)-icon-alert")
        harnessRequire(alert.buttons["OK"].waitForExistence(timeout: 5), app, "\(step): icon alert has no OK")
        alert.buttons["OK"].tap()
        harnessRequire(waitUntil(timeout: 10) { !appAlert.exists && !systemAlert.exists }, app, "\(step): icon alert did not close")
    }

    private func setDiscrete(_ app: XCUIApplication, on: Bool, step: String) {
        let toggle = discreteSwitch(app)
        harnessRequire(toggle.isSelected == !on, app, "\(step): discrete switch not in the expected starting state")
        toggle.tap()
        acknowledgeIconAlert(app, required: on, step)
        harnessRequire(waitUntil(timeout: 15) { self.discreteSwitch(app).isSelected == on }, app,
                       "\(step): discrete switch did not become \(on ? "on" : "off")")
    }

    private func assertCalculatorGate(_ app: XCUIApplication, _ step: String) {
        harnessRequire(key(app, "=").waitForExistence(timeout: 60), app, "\(step): calculator must show")
        harnessRequire(!app.staticTexts["Application preferences"].firstMatch.exists, app, "\(step): Settings visible before the PIN")
    }

    private func assertSettingsDirectly(_ app: XCUIApplication, _ step: String) {
        harnessRequire(waitForSettingsRoot(app), app, "\(step): Settings must appear without a calculator")
        harnessRequire(!key(app, "=").exists, app, "\(step): calculator must not show")
    }

    /// Best-effort restore, whatever state the app is left in (calculator gate with PIN 1234 or 4321, or normal):
    /// discrete off and the default PIN. Never asserts: a cleanup problem is attached, not failed.
    private func restoreNormalState() {
        XCUIApplication().terminate()
        let app = launchSettings(fixture: false)
        defer { app.terminate() }
        let root = app.staticTexts["Application preferences"].firstMatch
        let gate = key(app, "=")
        _ = waitUntil(timeout: 60) { root.exists || gate.exists }
        for pin in [["1", "2", "3", "4"], ["4", "3", "2", "1"]] where gate.exists {
            for label in ["C"] + pin + ["="] { if key(app, label).waitForExistence(timeout: 3) { key(app, label).tap() } }
            _ = waitUntil(timeout: 10) { root.exists }
        }
        guard waitUntil(timeout: 30, { root.exists }) else { shot(app, "cleanup-settings-not-reached"); return }
        func scrollTo(_ id: String) -> XCUIElement? {
            let row = settingsRow(app, id)
            for _ in 0..<14 where !(row.exists && row.isHittable) { app.swipeUp() }
            for _ in 0..<14 where !(row.exists && row.isHittable) { app.swipeDown() }
            return row.exists && row.isHittable ? row : nil
        }
        if let toggle = scrollTo("discrete_mode"), toggle.isSelected {
            toggle.tap()
            acknowledgeIconAlert(app, required: false, "teardown-disable")
            _ = waitUntil(timeout: 15) { !toggle.isSelected }
        }
        if let pinRow = scrollTo("calculator_pin") {
            pinRow.tap()
            let field = app.textViews.firstMatch
            if field.waitForExistence(timeout: 10) {
                field.tap()
                let existing = (field.value as? String) ?? ""
                if !existing.isEmpty { field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: existing.count + 2)) }
                field.typeText("1234")
                if app.buttons["Apply"].firstMatch.waitForExistence(timeout: 5) { app.buttons["Apply"].firstMatch.tap() }
                _ = closeSettingsEditor(app)
            } else { shot(app, "cleanup-pin-editor-missing") }
        }
        XCUIDevice.shared.press(.home)
        usleep(1_000_000)
    }

    func testToggleDiscreteFromSettings() {
        addTeardownBlock { [self] in restoreNormalState() }
        // Preconditions: discrete off, PIN 1234.
        var app = launchSettings(fixture: true)
        harnessRequire(waitForSettingsRoot(app), app, "Settings root did not appear")
        replaceSettingsText(app, rowKey: "calculator_pin", with: "1234")
        harnessRequire(closeSettingsEditor(app), app, "PIN editor did not close (1234)")

        // On -> relaunch -> calculator -> wrong PIN fails -> 1234 unlocks.
        setDiscrete(app, on: true, step: "enable")
        app = relaunch(app)
        assertCalculatorGate(app, "after enable")
        enter(app, ["9", "9", "9", "9", "="])
        harnessRequire(key(app, "C").exists && !app.staticTexts["Application preferences"].firstMatch.exists, app,
                       "Wrong PIN must not unlock")
        enter(app, ["C", "1", "2", "3", "4", "="])
        harnessRequire(waitForSettingsRoot(app), app, "PIN 1234 did not reach Settings")
        harnessRequire(!key(app, "=").exists, app, "Calculator still shown after unlock")

        // Off -> relaunch -> no calculator.
        setDiscrete(app, on: false, step: "disable")
        app = relaunch(app)
        assertSettingsDirectly(app, "after disable")

        // Change the PIN to 4321, discrete on again: the gate accepts only 4321.
        replaceSettingsText(app, rowKey: "calculator_pin", with: "4321")
        harnessRequire(closeSettingsEditor(app), app, "PIN editor did not close (4321)")
        setDiscrete(app, on: true, step: "re-enable")
        app = relaunch(app)
        assertCalculatorGate(app, "after re-enable")
        enter(app, ["1", "2", "3", "4", "="])
        harnessRequire(key(app, "C").exists && !app.staticTexts["Application preferences"].firstMatch.exists, app,
                       "The old PIN 1234 must no longer unlock")
        enter(app, ["C", "4", "3", "2", "1", "="])
        harnessRequire(waitForSettingsRoot(app), app, "PIN 4321 did not reach Settings")

        // Restoring discrete off and PIN 1234 is done by the teardown block (also after a failure).
        app.terminate()
    }
}
