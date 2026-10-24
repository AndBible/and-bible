import XCTest

/// Actual app-wide request retention, not a Boolean presentation-gate test.
/// Debug contract, through the existing bootstrap/files-port harness only:
/// -filesPortTest dialog-native|dialog-background -filesDialogToken UUID.
/// files-port-open presents the default production document picker. dialog-native posts only once
/// that real picker is attached; dialog-background posts on actual UIApplicationDidEnterBackground.
/// Post to the SAME Koin AppDialogController, message exactly "An error has occurred: TOKEN", confirm OK.
/// Keep returned request id and all actual onResult values; never auto-respond/cancel on lifecycle.
/// files-dialog-answered-once requires callbacks == [AppDialogResult.Ok] and the tracked id absent
/// from pending/progress. files-dialog-error reports any contract violation after modal dismissal.
/// Hidden pending identity/zero callbacks are app-process evidence, NOT a UI accessibility assertion:
/// structured Main-thread logs must record token, actual post id, pending/progress ids and callback
/// results at enqueue under actual native/background coverage and before rendering. Missing or
/// contradictory logs invalidate that evidence even if the UI recovery below passes.
final class FilesDialogUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
        addUIInterruptionMonitor(withDescription: "Notification permission only") { alert in
            guard alert.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] 'notifications'")).count > 0,
                  alert.buttons["Don't Allow"].exists else { return false }
            alert.buttons["Don't Allow"].tap()
            return true
        }
    }

    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    private func evidence(_ app: XCUIApplication, _ name: String) {
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
        let tree = XCTAttachment(string: app.debugDescription + "\nSpringBoard:\n" +
            XCUIApplication(bundleIdentifier: "com.apple.springboard").debugDescription)
        tree.name = "tree-\(name)"
        tree.lifetime = .keepAlways
        add(tree)
    }

    private func visible(_ target: XCUIElement, app: XCUIApplication, step: String) {
        let found = target.waitForExistence(timeout: 30)
        evidence(app, "\(step)-\(found ? "visible" : "timeout")")
        XCTAssertTrue(found, "Missing real queue/UI evidence at \(step)")
    }

    private func wait(_ app: XCUIApplication, step: String, condition: @escaping () -> Bool) {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in condition() }, object: nil)
        let result = XCTWaiter.wait(for: [expectation], timeout: 30)
        evidence(app, step)
        XCTAssertEqual(result, .completed, step)
    }

    private func runRetention(background: Bool) {
        let token = UUID().uuidString
        let app = XCUIApplication()
        app.launchArguments = ["-task6ResetCrashState", "-standardTestFixture", "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-filesPortTest", background ? "dialog-background" : "dialog-native",
                               "-filesDialogToken", token, "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        let permission = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if permission.waitForExistence(timeout: 5),
           permission.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] 'notifications'")).count > 0,
           permission.buttons["Don't Allow"].exists { permission.buttons["Don't Allow"].tap() }
        visible(element(app, "files-port-idle"), app: app, step: "dialog-port-ready")
        let trigger = element(app, "files-port-open")
        XCTAssertTrue(trigger.isHittable && trigger.isEnabled)
        trigger.tap()
        let cancel = app.buttons["Cancel"].firstMatch
        visible(cancel, app: app, step: "dialog-real-picker-cancel")
        visible(app.otherElements["Browse View (Picker)"].firstMatch, app: app, step: "dialog-real-picker-content")
        XCTAssertTrue(cancel.isHittable && cancel.isEnabled)
        let message = app.staticTexts.matching(NSPredicate(format: "label == %@", "An error has occurred: \(token)")).firstMatch
        if background {
            XCUIDevice.shared.press(.home)
            XCTAssertTrue(app.wait(for: .runningBackground, timeout: 20), "App never entered actual background")
            app.activate()
            XCTAssertTrue(app.wait(for: .runningForeground, timeout: 20))
            visible(cancel, app: app, step: "dialog-picker-retained-after-foreground")
        }
        evidence(app, "dialog-native-still-covers-app")
        XCTAssertFalse(message.exists, "App-wide dialog overpresented the actual native picker")
        XCTAssertTrue(cancel.isHittable)
        cancel.tap()
        wait(app, step: "dialog-native-picker-dismissed") { !cancel.exists }
        // Actual recovery is UI evidence; hidden pending id and zero callbacks require structured logs.
        visible(message, app: app, step: "dialog-exact-queued-message")
        let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        XCTAssertTrue(ok.isHittable && ok.isEnabled)
        ok.tap()
        wait(app, step: "dialog-message-dismissed") { !message.exists }
        visible(element(app, "files-dialog-answered-once"), app: app, step: "dialog-exactly-one-real-ok")
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 20))
        app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 20))
        visible(element(app, "files-dialog-answered-once"), app: app, step: "dialog-one-ok-after-lifecycle-cycle")
        XCTAssertFalse(message.exists, "Answered request reappeared after foreground")
        XCTAssertFalse(element(app, "files-dialog-error").exists)
    }

    func testQueuedRequestWaitsForActualNativePickerDismissal() {
        runRetention(background: false)
    }

    func testQueuedRequestSurvivesActualBackgroundAndForeground() {
        runRetention(background: true)
    }
}
