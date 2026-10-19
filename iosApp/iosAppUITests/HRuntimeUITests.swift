import XCTest

/// Remaining reachable H flows on real hosts. Controller-owned runs must retain screenshots and native ownership logs.
final class HRuntimeUITests: RealAppUITestCase {
    private let planTitle = "1-Year through Bible"
    private let fixtureProvider = "H language UI fixture"

    private func require(_ condition: Bool, _ app: XCUIApplication, _ step: String,
                         file: StaticString = #filePath, line: UInt = #line) {
        if !condition { shot(app, step); attachTree(app, step) }
        XCTAssertTrue(condition, step, file: file, line: line)
    }

    private func text(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func tapText(_ app: XCUIApplication, _ label: String, swipes: Int = 12) {
        let row = text(app, label)
        _ = row.waitForExistence(timeout: 5)
        for _ in 0..<swipes where !row.isHittable { app.swipeUp() }
        require(row.exists && row.isHittable, app, "missing-row-\(label)")
        row.tap()
    }

    /// A hittable edge fragment can still be moving after a swipe; require the entire actionable row inside.
    private func stableInterior(_ app: XCUIApplication, _ target: XCUIElement, _ step: String) {
        let viewport = app.frame
        let interior = CGRect(x: viewport.minX, y: viewport.minY + viewport.height * 0.2,
                              width: viewport.width, height: viewport.height * 0.5)
        for _ in 0..<20 {
            if target.exists && target.isHittable && interior.contains(target.frame) { break }
            if target.exists && target.frame.midY < interior.minY { app.swipeDown() }
            else { app.swipeUp() }
        }
        var previous = CGRect.null
        var stableSamples = 0
        let ready = waitUntil(timeout: 10) {
            guard target.exists && target.isHittable else { stableSamples = 0; return false }
            let frame = target.frame
            guard interior.contains(frame) else { stableSamples = 0; return false }
            stableSamples = frame == previous ? stableSamples + 1 : 0
            previous = frame
            return stableSamples >= 2
        }
        require(ready, app, "stable-interior-\(step)")
        shot(app, "before-\(step)")
        attachTree(app, "before-\(step)")
    }

    private func button(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func tapButton(_ app: XCUIApplication, _ label: String) {
        let target = button(app, label)
        require(target.waitForExistence(timeout: 10) && target.isHittable, app, "missing-button-\(label)")
        target.tap()
    }

    private func loadPlan(_ app: XCUIApplication) {
        let loaded = element(app, "debug-open-settings")
        if !loaded.waitForExistence(timeout: 5) { tapText(app, planTitle) }
        require(loaded.waitForExistence(timeout: 30), app, "plan-not-loaded")
        require(button(app, planTitle).exists, app, "actual-plan-title-missing")
    }

    private func openSettings(_ app: XCUIApplication) {
        let settings = element(app, "debug-open-settings")
        require(settings.waitForExistence(timeout: 10) && settings.isHittable, app, "settings-entry-missing")
        settings.tap()
    }

    private func relaunchPlan(_ app: XCUIApplication) -> XCUIApplication {
        sendToBackground(app)
        app.terminate()
        // Deliberately omit resetData AND standardTestFixture: preferences under test must survive unchanged.
        let next = launch(["-featureScenario", "plan-settings"])
        require(element(next, "debug-feature-ready").waitForExistence(timeout: 60), next, "relaunch-feature-not-ready")
        require(!element(next, "welcome-screen").exists, next, "relaunch-unexpected-welcome")
        loadPlan(next)
        return next
    }

    private func openStartDate(_ app: XCUIApplication) {
        tapButton(app, "Menu")
        tapButton(app, "Set Start Date\u{2026}")
        // Material3 can start in calendar or narrow-width input mode. Only the documented mode switch is used.
        let inputMode = button(app, "Switch to text input mode")
        if inputMode.waitForExistence(timeout: 3) { inputMode.tap() }
        require(app.textViews.count == 1 && app.textViews.firstMatch.isHittable,
                app, "date-input-AX-contract-unknown")
    }

    func testLoadedPlanPastStartDatePersistsOnReopen() {
        var app = freshReading(scenario: "plan-settings")
        loadPlan(app)
        openStartDate(app)
        let field = app.textViews.firstMatch
        let original = field.value as? String ?? ""
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "MMddyyyy"
        let target = Calendar(identifier: .gregorian).date(byAdding: .day, value: -7, to: Date())!
        let digits = formatter.string(from: target)
        field.tap()
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: original.count) + digits)
        let expected = "\(digits.prefix(2))/\(digits.dropFirst(2).prefix(2))/\(digits.suffix(4))"
        require(waitUntil(timeout: 5) { (field.value as? String) == expected }, app, "date-input-format-or-value-mismatch")
        require(original != expected, app, "date-edit-was-no-op")
        shot(app, "plan-distinct-past-start-date")
        tapButton(app, "OK")
        require(waitUntil(timeout: 10) { !field.exists }, app, "date-picker-not-confirmed")
        openStartDate(app)
        require((app.textViews.firstMatch.value as? String) == expected, app, "date-not-retained-on-reopen")
        tapButton(app, "Cancel")
        app = relaunchPlan(app)
        openStartDate(app)
        require((app.textViews.firstMatch.value as? String) == expected, app, "date-not-persisted-across-process")
        shot(app, "plan-past-date-after-relaunch")
        tapButton(app, "Cancel")
    }

    private func fillProviderField(_ app: XCUIApplication, _ label: String, value: String) {
        // TextView is the observed iOS Compose editable role; fail with AX evidence rather than trying arbitrary roles.
        let field = app.textViews.matching(NSPredicate(format: "label CONTAINS %@", label)).firstMatch
        require(field.waitForExistence(timeout: 10) && field.isHittable, app, "provider-field-AX-\(label)")
        field.tap()
        // Typing right after a focus switch (keyboard already up) dropped the first key. Wait for real focus; no retyping,
        // so an actual first-key drop is still caught by the exact readback below.
        let focusAttribute = NSSelectorFromString("hasKeyboardFocus")
        if field.responds(to: focusAttribute) {
            let focused = waitUntil(timeout: 5) { (field.value(forKey: "hasKeyboardFocus") as? Bool) == true }
            require(focused, app, "provider-field-no-keyboard-focus-\(label)")
        } else {
            require(waitUntil(timeout: 5) { app.keyboards.firstMatch.exists }, app, "provider-field-no-keyboard-\(label)")
        }
        field.typeText(value)
        require(waitUntil(timeout: 5) { (field.value as? String) == value }, app, "provider-field-readback-\(label)")
    }

    func testAiLanguageListWithDisposableCustomProvider() {
        let app = freshReading(scenario: "plan-settings")
        loadPlan(app)
        openSettings(app)
        let aiRows = app.buttons.containing(.staticText, identifier: "AI Settings")
        let aiRow = aiRows.firstMatch
        stableInterior(app, aiRow, "ai-settings-entry")
        require(aiRows.count == 1, app, "ai-settings-parent-not-unique")
        aiRow.tap()
        require(waitUntil(timeout: 15) {
            !self.text(app, "Application preferences").exists && self.button(app, "Configure Connection").isHittable
        }, app, "ai-settings-route-not-entered")
        tapButton(app, "Configure Connection")
        require(text(app, "No providers configured").waitForExistence(timeout: 15), app, "expected-empty-provider-fixture")
        require(!text(app, "AI response language").exists, app, "language-row-must-be-provider-gated")
        tapText(app, "Providers")
        tapButton(app, "Add provider")
        let accept = button(app, "I accept and take full responsibility")
        if accept.waitForExistence(timeout: 3) { accept.tap() }
        // The sheet is lazily laid out and "Custom (OpenAI compatible)" is its LAST row, below the fold: scroll
        // (bounded) until the real row exists, then require full on-screen geometry that is stable. A band cannot
        // contain a last row, so none is used.
        let customText = app.staticTexts.matching(NSPredicate(format: "label == %@", "Custom (OpenAI compatible)")).firstMatch
        for _ in 0..<15 where !customText.exists { nudgeList(app, up: true) }
        require(customText.exists && waitForStableOnScreen(app, customText), app, "custom-provider-type-row-not-settled")
        let customRows = app.buttons.containing(.staticText, identifier: "Custom (OpenAI compatible)")
        let customRow = customRows.firstMatch
        require(customRow.waitForExistence(timeout: 10) && customRows.count == 1 && customRow.isHittable, app, "custom-provider-type-row-missing")
        customRow.tap()
        fillProviderField(app, "Provider name", value: fixtureProvider)
        fillProviderField(app, "API key", value: "not-a-real-key")
        fillProviderField(app, "API endpoint", value: "http://127.0.0.1:9")
        // Custom disables dynamic model prefetch. Never Quick Setup, Test connection, Models, or run a prompt.
        tapButton(app, "OK")
        require(text(app, fixtureProvider).waitForExistence(timeout: 15), app, "custom-provider-not-saved")
        tapButton(app, "Back")
        tapText(app, "AI response language")
        attachTree(app, "ai-language-exact-choice-list")
        shot(app, "ai-language-real-list")
        require(text(app, "Custom\u{2026}").exists, app, "language-custom-choice-missing")
        let wasFinnish = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "fi: Suomi"))
            .allElementsBoundByIndex.contains { $0.isSelected }
        require(!wasFinnish, app, "language-fixture-already-finnish")
        tapText(app, "fi: Suomi")
        require(waitUntil(timeout: 10) { !text(app, "fi: Suomi").exists }, app, "language-list-not-dismissed")
        tapText(app, "AI response language")
        let finnish = text(app, "fi: Suomi")
        for _ in 0..<12 where !finnish.isHittable { app.swipeUp() }
        let selectedFinnish = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "fi: Suomi"))
            .allElementsBoundByIndex.contains { $0.isSelected }
        require(finnish.exists && selectedFinnish, app, "language-selection-not-retained-AX")
        shot(app, "ai-language-finnish-retained")
        // Selecting the same exact choice closes the real list; application language remains English.
        finnish.tap()
        for _ in 0..<12 where !text(app, "Providers").isHittable { app.swipeDown() }
        tapText(app, "Providers", swipes: 0)
        tapText(app, fixtureProvider)
        tapButton(app, "Delete")
        require(text(app, "Delete provider \"\(fixtureProvider)\"? Prompts using this provider will revert to the default.")
            .waitForExistence(timeout: 10), app, "named-provider-delete-confirmation-missing")
        tapButton(app, "Yes")
        // Delete closes its confirmation, not the still-open editor (same real common flow on Android).
        require(waitUntil(timeout: 10) { !self.text(app, "Delete provider \"\(self.fixtureProvider)\"? Prompts using this provider will revert to the default.").exists },
                app, "provider-delete-confirmation-not-dismissed")
        tapButton(app, "Cancel")
        require(waitUntil(timeout: 10) { !text(app, fixtureProvider).exists }, app, "fixture-provider-not-deleted")
        tapButton(app, "Back")
        require(text(app, "No providers configured").waitForExistence(timeout: 10), app, "provider-cleanup-not-reflected")
        require(!text(app, "AI response language").exists, app, "language-gate-not-restored-after-cleanup")
        shot(app, "ai-fixture-cleaned-up")
    }

    private func bluetoothSwitch(_ app: XCUIApplication) -> XCUIElement {
        let toggle = app.switches.matching(NSPredicate(format: "label CONTAINS 'Enable Bluetooth media buttons'")).firstMatch
        // It is the last settings row, so no interior band is reachable; require real full-screen geometry that is stable.
        for _ in 0..<20 where !(toggle.exists && toggle.isHittable) { app.swipeUp() }
        require(waitForStableOnScreen(app, toggle), app, "bluetooth-setting-not-stable-on-screen")
        shot(app, "before-bluetooth-setting")
        attachTree(app, "before-bluetooth-setting")
        return toggle
    }

    private func setBluetooth(_ app: XCUIApplication, enabled: Bool) {
        let toggle = bluetoothSwitch(app)
        // Tap the left row area: the thumb and the DEBUG overlay are at the right edge.
        if toggle.isSelected != enabled { toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.5)).tap() }
        require(waitUntil(timeout: 10) { toggle.isSelected == enabled }, app, "bluetooth-setting-not-\(enabled)")
        shot(app, "bluetooth-setting-\(enabled)")
    }

    private func speakLoadedPlan(_ app: XCUIApplication, phase: String) {
        let speak = button(app, "Speak")
        require(speak.waitForExistence(timeout: 10) && speak.isHittable, app, "\(phase)-daily-speak-missing")
        speak.tap()
        require(button(app, "Stop").waitForExistence(timeout: 20), app, "\(phase)-real-speech-not-started")
        shot(app, "\(phase)-daily-speaking")
        attachTree(app, "\(phase)-daily-speaking")
        // Native attach/skip and owned-target logs, not this visible control, prove Bluetooth ownership.
        tapButton(app, "Stop")
        require(waitUntil(timeout: 10) { !button(app, "Stop").exists }, app, "\(phase)-speech-not-stopped")
    }

    func testBluetoothOffAndOnAcrossUnnormalizedProcesses() {
        var app = freshReading(scenario: "plan-settings")
        loadPlan(app) // This process may already own SpeakControl; only subsequent processes count.
        openSettings(app)
        setBluetooth(app, enabled: false)
        app = relaunchPlan(app)
        openSettings(app)
        require(!bluetoothSwitch(app).isSelected, app, "bluetooth-off-not-persisted")
        element(app, "debug-feature-back").tap()
        speakLoadedPlan(app, phase: "bluetooth-off-process")
        openSettings(app)
        setBluetooth(app, enabled: true)
        app = relaunchPlan(app)
        openSettings(app)
        require(bluetoothSwitch(app).isSelected, app, "bluetooth-on-not-persisted")
        element(app, "debug-feature-back").tap()
        speakLoadedPlan(app, phase: "bluetooth-on-process")
        sendToBackground(app)
    }
}
