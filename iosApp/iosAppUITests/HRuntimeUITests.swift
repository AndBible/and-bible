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
        let switched = inputMode.waitForExistence(timeout: 3)
        if switched { inputMode.tap() }
        require(app.textViews.count == 1 && app.textViews.firstMatch.isHittable,
                app, "date-input-AX-contract-unknown")
        if switched {
            // Switching mode raises the keyboard by itself and slides the dialog up: let both settle before returning.
            require(app.keyboards.firstMatch.waitForExistence(timeout: 10), app, "date-input-keyboard-not-shown")
            require(waitForStableOnScreen(app, app.textViews.firstMatch), app, "date-input-field-not-settled")
        }
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
        // The dialog may have moved since openStartDate returned; a vanished dialog must fail diagnostically.
        require(field.waitForExistence(timeout: 5) && waitForStableOnScreen(app, field), app, "date-input-field-not-settled-before-typing")
        // A redundant tap landed on the scrim and dismissed the dialog. Tap only if the field really lacks keyboard focus
        // (the retained AX tree prints it as "Keyboard Focused").
        if !field.debugDescription.contains("Keyboard Focused") {
            field.tap()
            require(waitUntil(timeout: 5) { field.debugDescription.contains("Keyboard Focused") }, app, "date-input-no-keyboard-focus")
        }
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
        // Typing right after a focus switch dropped the first key. Wait for THIS field to report keyboard focus: the retained
        // AX tree prints it as "Keyboard Focused" in the element's own description. No keyboards.exists fallback (the previous
        // field's keyboard is already up) and no retyping, so a genuine first-key drop is still caught by the readback.
        let focused = waitUntil(timeout: 5) { field.debugDescription.contains("Keyboard Focused") }
        require(focused, app, "provider-field-no-keyboard-focus-\(label)")
        if label == "API endpoint" {
            // The keyboard re-lays out for a URL field after focus; wait for its real '.com' key.
            require(waitUntil(timeout: 5) { app.keys[".com"].exists }, app, "provider-field-no-url-keyboard-\(label)")
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
        // AI Settings is a lazily composed, last-section row: it is absent from the AX tree until scrolled to, so scroll
        // (bounded, left of the DEBUG overlay) first. A mid-screen band cannot hold it on iPad (820x1180, about 0.73-0.79 of
        // the height with the list at its end), so the real requirement is full on-screen, hittable, stable geometry below.
        for _ in 0..<15 where !(aiRow.exists && aiRow.isHittable) { nudgeList(app, up: true) }
        require(aiRow.waitForExistence(timeout: 10) && waitForStableOnScreen(app, aiRow), app, "ai-settings-entry-not-settled")
        shot(app, "before-ai-settings-entry")
        attachTree(app, "before-ai-settings-entry")
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
        // The choice list is a bottom sheet that slides in: evidence is taken only once its first real row is settled.
        let appLanguage = text(app, "App language (English)")
        require(appLanguage.waitForExistence(timeout: 10) && waitForStableOnScreen(app, appLanguage), app, "language-sheet-not-settled")
        attachTree(app, "ai-language-exact-choice-list")
        shot(app, "ai-language-real-list")
        // "Custom…" is appended after the ~60 locales in a lazy list: scroll (bounded) to it, require it settled, never tap it.
        let customChoice = text(app, "Custom\u{2026}")
        for _ in 0..<40 where !customChoice.exists { nudgeList(app, up: true) }
        require(customChoice.exists && waitForStableOnScreen(app, customChoice), app, "language-custom-choice-missing")
        // No downward drags: they dismiss the sheet from its header band. Close it with the real Close button, reopen (the
        // list restarts at the top) and reach the real Finnish row with upward content drags only.
        let firstClose = button(app, "Close")
        require(firstClose.exists && firstClose.isHittable, app, "language-sheet-close-missing-before-reopen")
        firstClose.tap()
        require(waitUntil(timeout: 10) { !self.button(app, "Close").exists && !self.text(app, "Custom\u{2026}").exists }, app, "language-sheet-not-closed-before-reopen")
        tapText(app, "AI response language")
        require(button(app, "Close").waitForExistence(timeout: 10), app, "language-sheet-reopen-for-finnish-missing")
        let finnishChoice = text(app, "fi: Suomi")
        for _ in 0..<40 where !(finnishChoice.exists && finnishChoice.isHittable) {
            // The underlying Connection settings screen has no Close button in the observed runs, so Close means the sheet is open.
            require(button(app, "Close").exists, app, "language-sheet-dismissed-while-scrolling")
            nudgeSheetList(app)
        }
        require(button(app, "Close").exists, app, "language-sheet-dismissed-while-scrolling")
        require(finnishChoice.exists && waitForStableOnScreen(app, finnishChoice), app, "language-finnish-choice-not-settled")
        let wasFinnish = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "fi: Suomi"))
            .allElementsBoundByIndex.contains { $0.isSelected }
        require(!wasFinnish, app, "language-fixture-already-finnish")
        tapText(app, "fi: Suomi")
        require(waitUntil(timeout: 10) { !text(app, "fi: Suomi").exists }, app, "language-list-not-dismissed")
        // Summary text: AiSettingsServiceImpl.aiLanguageLabel (AiSettingsServiceImpl.kt:101-106) returns "$displayName ($tag)",
        // with displayName from IosLocaleSupport.displayLanguage (IosLocaleSupport.kt:68-70), the language named in its own
        // locale: "suomi (fi)", the same text the Android run read back.
        require(text(app, "suomi (fi)").waitForExistence(timeout: 10), app, "ai-language-summary-not-suomi-fi")
        require(button(app, "AI response language, suomi (fi)").exists, app, "ai-language-row-label-not-suomi-fi")
        // Reopen: the selection must be retained. The list is moved only by bounded sheet nudges (a fast fling left it
        // decelerating and swallowed a tap), the real Finnish row must settle, and it is observed but NOT tapped again.
        tapText(app, "AI response language")
        require(button(app, "Close").waitForExistence(timeout: 10), app, "language-reopen-sheet-missing")
        let finnish = text(app, "fi: Suomi")
        for _ in 0..<40 where !(finnish.exists && finnish.isHittable) {
            require(button(app, "Close").exists, app, "language-sheet-dismissed-while-scrolling")
            nudgeSheetList(app)
        }
        require(finnish.exists && waitForStableOnScreen(app, finnish), app, "language-reopen-finnish-not-settled")
        let selectedFinnish = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "fi: Suomi"))
            .allElementsBoundByIndex.contains { $0.isSelected }
        require(selectedFinnish, app, "language-selection-not-retained-AX")
        shot(app, "ai-language-finnish-retained")
        // Close with the header button (no downward drag, which can dismiss a bottom sheet): the choice must be unchanged.
        let closeSheet = button(app, "Close")
        require(closeSheet.exists && closeSheet.isHittable, app, "language-sheet-close-missing")
        closeSheet.tap()
        require(waitUntil(timeout: 10) { !self.button(app, "Close").exists && !self.text(app, "fi: Suomi").exists }, app, "language-sheet-not-closed")
        require(button(app, "AI response language, suomi (fi)").waitForExistence(timeout: 10), app, "ai-language-changed-by-close")
        // Restore the application-language default through the real first row. A reopened sheet starts at the list top, so
        // the row is visible at once; it is not Selected now (Finnish is), which makes the later restore non-vacuous.
        // Source text "App language (English)": aiLanguageAppDefault, AiSettingsServiceImpl.kt:102.
        tapText(app, "AI response language")
        require(button(app, "Close").waitForExistence(timeout: 10), app, "language-restore-sheet-missing")
        let restoreRow = text(app, "App language (English)")
        require(restoreRow.waitForExistence(timeout: 10) && waitForStableOnScreen(app, restoreRow), app, "language-restore-row-not-settled")
        let defaultSelectedBefore = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "App language (English)"))
            .allElementsBoundByIndex.contains { $0.isSelected }
        require(!defaultSelectedBefore, app, "language-default-already-selected")
        restoreRow.tap()
        require(waitUntil(timeout: 10) { !self.button(app, "Close").exists && !self.text(app, "fi: Suomi").exists }, app, "language-restore-sheet-not-dismissed")
        require(button(app, "AI response language, App language (English)").waitForExistence(timeout: 10), app, "ai-language-summary-not-restored")
        require(!text(app, "suomi (fi)").exists, app, "ai-language-finnish-summary-still-shown")
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
