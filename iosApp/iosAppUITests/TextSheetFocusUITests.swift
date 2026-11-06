import XCTest

/// #4005 on iOS: a settings text sheet focuses its editor when it opens, so typing works without tapping the field.
/// Every check below types through the app's first responder (`app.typeText`), never through a tap on the field.
/// The reading-view origin (a WKWebView holding focus before the sheet) is `ReadingUITests.testTextSheetFocusFromReadingPane`.
final class TextSheetFocusUITests: RealAppUITestCase {
    private lazy var syncUI = SyncHarness(test: self)

    private func editor(_ app: XCUIApplication) -> XCUIElement { app.textViews.firstMatch }

    private func awaitSync(_ app: XCUIApplication) {
        let title = app.staticTexts["Device synchronization"].firstMatch
        harnessRequire(title.waitForExistence(timeout: 60) && settingsRow(app, "sync_adapter").waitForExistence(timeout: 30),
                       app, "Device synchronization screen did not appear")
    }

    private func chooseNextcloud(_ app: XCUIApplication) {
        _ = revealSettingsRow(app, "sync_adapter")
        if settingsRow(app, "sync_adapter").label.contains("NextCloud") { return }
        settingsRow(app, "sync_adapter").tap()
        let choice = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "NextCloud")).firstMatch
        harnessRequire(choice.waitForExistence(timeout: 10), app, "NextCloud choice missing")
        choice.tap()
        harnessRequire(waitUntil(timeout: 15) { self.settingsRow(app, "sync_adapter").label.contains("NextCloud") },
                       app, "Adapter row did not show NextCloud after choosing it")
    }

    /// Opens the row's editor and waits for keyboard focus WITHOUT touching the field. Returns the field.
    private func openFocused(_ app: XCUIApplication, _ row: String, _ step: String, sensitive: Bool = false) -> XCUIElement {
        syncUI.reveal(app, row).tap()
        let field = editor(app)
        harnessRequire(field.waitForExistence(timeout: 10), app, "\(row): editor missing")
        let focused = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "hasKeyboardFocus == true"),
                                                                     object: field)], timeout: 10) == .completed
        let keyboard = app.keyboards.firstMatch.exists
        let note = XCTAttachment(string: "\(step): focused=\(focused) softwareKeyboard=\(keyboard)")
        note.name = "focus-\(step)"; note.lifetime = .keepAlways; add(note)
        if !sensitive { shot(app, "focus-\(step)-opened") }
        harnessRequire(focused, app, "\(row): the editor did not get keyboard focus when the sheet opened")
        return field
    }

    private func cancel(_ app: XCUIApplication, _ field: XCUIElement, _ row: String) {
        let cancel = app.buttons["Cancel"].firstMatch
        harnessRequire(cancel.waitForExistence(timeout: 5), app, "\(row): Cancel missing")
        cancel.tap()
        harnessRequire(waitUntil(timeout: 10) { !field.exists }, app, "\(row): the sheet did not close after Cancel")
    }

    /// Checklist 1, 2 and 3: Server URL, User name and Password each focus on open (also on a reopen after Cancel);
    /// typed text replaces the selected old value; the password stays masked; letters and Backspace go to the field.
    private func checkSyncEditors(_ app: XCUIApplication, _ prefix: String) {
        // Server URL: first a known value, so the reopen below has old text that must be selected and replaced.
        var field = openFocused(app, "Server URL", "\(prefix)-url")
        app.typeText("https://ios-focus.examplex")
        app.typeText(XCUIKeyboardKey.delete.rawValue)
        harnessRequire(waitUntil(timeout: 5) { (field.value as? String) == "https://ios-focus.example" }, app,
                       "Server URL: typed text without a field tap reads '\(field.value as? String ?? "nil")'")
        shot(app, "focus-\(prefix)-url-typed")
        let apply = app.buttons["Apply"].firstMatch
        harnessRequire(apply.waitForExistence(timeout: 5), app, "Server URL: Apply missing")
        apply.tap()
        harnessRequire(waitUntil(timeout: 10) { !field.exists }, app, "Server URL: the sheet did not close after Apply")

        field = openFocused(app, "Server URL", "\(prefix)-url-reopen")
        app.typeText("https://replaced.example")
        harnessRequire(waitUntil(timeout: 5) { (field.value as? String) == "https://replaced.example" }, app,
                       "Server URL reopen: the old value was not selected and replaced, reads '\(field.value as? String ?? "nil")'")
        cancel(app, field, "Server URL")

        field = openFocused(app, "User name", "\(prefix)-user")
        app.typeText("focus-user")
        harnessRequire(waitUntil(timeout: 5) { ((field.value as? String) ?? "").hasSuffix("focus-user") }, app,
                       "User name: typed text missing, reads '\(field.value as? String ?? "nil")'")
        cancel(app, field, "User name")

        field = openFocused(app, "Password", "\(prefix)-password", sensitive: true)
        app.typeText("notasecret")
        let value = (field.value as? String) ?? ""
        harnessRequire(!value.contains("notasecret") && waitUntil(timeout: 5) { !((field.value as? String) ?? "").isEmpty },
                       app, "Password: typed text is not masked or did not arrive")
        shot(app, "focus-\(prefix)-password-masked")
        cancel(app, field, "Password")
    }

    func testSyncTextSheetsFocusOnOpen() {
        let app = launch(["-standardTestFixture", "-h2SyncSettings"])
        awaitSync(app)
        chooseNextcloud(app)
        checkSyncEditors(app, "light")
        app.terminate()
    }

    /// Checklist 4: dark mode and Disable animations (the sheet appears without a slide; focus must still arrive and the
    /// sheet must settle fully with the keyboard).
    func testSyncTextSheetsFocusDarkWithoutAnimations() {
        let app = launch(["-standardTestFixture", "-task5Settings", "-night_mode_pref3", "manual", "-night_mode_pref", "YES"])
        harnessRequire(waitForSettingsRoot(app), app, "Settings root did not appear")
        let toggle = revealSettingsRow(app, "disable_animations")
        let wasOn = (toggle.value as? String) == "1" || toggle.isSelected
        if !wasOn { toggle.tap() }
        defer {
            // Leave the setting as found for later classes.
            if !wasOn, app.state == .runningForeground {
                app.terminate()
                let again = launch(["-standardTestFixture", "-task5Settings"])
                if waitForSettingsRoot(again) { revealSettingsRow(again, "disable_animations").tap() }
                again.terminate()
            }
        }
        _ = revealSettingsRow(app, "sync_settings_shortcut")
        settingsRow(app, "sync_settings_shortcut").tap()
        awaitSync(app)
        chooseNextcloud(app)
        checkSyncEditors(app, "dark-noanim")
    }
}
