import XCTest

/// H2 Task 9: shared helpers for the Settings-driven acceptance classes (SyncSettingsUITests, DiscreteSettingsUITests).
/// Rows are found by their Compose test tag `settings-row-<key>`; nothing depends on iPhone geometry.
extension RealAppUITestCase {
    func settingsRow(_ app: XCUIApplication, _ key: String) -> XCUIElement {
        element(app, "settings-row-\(key)")
    }

    /// Scrolls a lazily composed settings row into a hittable, frame-stable position (bounded) and returns it.
    func revealSettingsRow(_ app: XCUIApplication, _ key: String, file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let row = settingsRow(app, key)
        for _ in 0..<14 {
            if row.exists && row.isHittable { break }
            app.swipeUp()
        }
        if !(row.exists && row.isHittable) {
            // The row may be above the viewport after an earlier scroll.
            for _ in 0..<14 where !(row.exists && row.isHittable) { app.swipeDown() }
        }
        harnessRequire(row.exists && row.isHittable, app, "Settings row not reachable: \(key)", file: file, line: line)
        var previous = CGRect.null
        var stable = 0
        _ = waitUntil(timeout: 10) {
            let frame = row.frame
            stable = frame == previous ? stable + 1 : 0
            previous = frame
            return stable >= 2
        }
        return row
    }

    /// Waits for the real Settings root (route `-task5Settings`).
    func waitForSettingsRoot(_ app: XCUIApplication, timeout: TimeInterval = 60) -> Bool {
        app.staticTexts["Application preferences"].firstMatch.waitForExistence(timeout: timeout)
    }

    /// Opens the text editor of a settings row, replaces its content with `value` and applies it.
    /// Never used for secrets: the typed value is asserted back. Returns false when the editor could not be driven.
    @discardableResult
    func replaceSettingsText(_ app: XCUIApplication, rowKey: String, with value: String) -> Bool {
        _ = revealSettingsRow(app, rowKey)
        settingsRow(app, rowKey).tap()
        let field = app.textViews.firstMatch
        harnessRequire(field.waitForExistence(timeout: 10) && app.textViews.count == 1, app, "Editor text field missing: \(rowKey)")
        guard field.exists else { return false }
        // Tap the trailing edge so the caret lands at the END: a plain tap lands mid-text and the deletes then only
        // remove the text before the caret.
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.5)).tap()
        let existing = (field.value as? String) ?? ""
        if !existing.isEmpty {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: existing.count + 4))
        }
        if !value.isEmpty { field.typeText(value) }
        harnessRequire((field.value as? String ?? "") == value, app, "Editor did not hold the typed value for \(rowKey)")
        let apply = app.buttons["Apply"].firstMatch
        harnessRequire(apply.waitForExistence(timeout: 5), app, "Apply missing for \(rowKey)")
        apply.tap()
        return true
    }

    func closeSettingsEditor(_ app: XCUIApplication) -> Bool {
        let field = app.textViews.firstMatch
        return waitUntil(timeout: 10) { !field.exists }
    }
}
