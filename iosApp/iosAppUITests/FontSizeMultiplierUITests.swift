import XCTest

final class FontSizeMultiplierUITests: RealAppUITestCase {
    override var usesReadingDomProbe: Bool { true }

    func testApplicationFontMultiplierUpdatesOpenReading() {
        ensureKJVInstalled()
        let app = launch(["-standardTestFixture", "-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16"])
        defer { app.terminate() }
        harnessRequire(element(app, "reading-host").waitForExistence(timeout: 90), app, "Reading host")
        let before = settledDomVerse16(app, "multiplier-before")
        harnessRequire(before.count == 4, app, "John 3:16 DOM baseline")
        shot(app, "multiplier-before")

        let menu = app.buttons["Menu"].firstMatch
        harnessRequire(menu.waitForExistence(timeout: 15), app, "Reading menu")
        menu.tap()
        let preferences = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Application preferences")).firstMatch
        for _ in 0..<6 where !preferences.exists { app.swipeUp(); usleep(1_200_000) }
        harnessRequire(preferences.exists, app, "Application preferences drawer row")
        _ = waitUntil(timeout: 4) { preferences.isHittable }
        for _ in 0..<8 where !preferences.isHittable { app.swipeUp(); usleep(1_200_000) }
        harnessRequire(preferences.isHittable, app, "Application preferences drawer row is hittable")
        var last = CGRect.null
        _ = waitUntil(timeout: 5) { let frame = preferences.frame; defer { last = frame }; return frame == last }
        preferences.tap()
        harnessRequire(waitForSettingsRoot(app), app, "Application preferences screen")

        let row = revealSettingsRow(app, "font_size_multiplier")
        // Compose exposes this slider as an Other with a numeric value, not an XCUI slider.
        let slider = row.descendants(matching: .any).matching(NSPredicate(format: "value MATCHES '[0-9]+(\\.[0-9]+)?'")).firstMatch
        harnessRequire(slider.waitForExistence(timeout: 10), app, "Font multiplier slider")
        let initial = (slider.value as? Double) ?? (slider.value as? String).flatMap(Double.init)
        harnessRequire(initial == 100, app, "Font multiplier starts at 100")
        let width = slider.frame.width
        harnessRequire(width > 48, app, "Font multiplier slider has usable width")
        func offset(_ value: Double) -> CGFloat {
            CGFloat((24 + (value - 10) / 490 * (Double(width) - 48)) / Double(width))
        }
        let start = slider.coordinate(withNormalizedOffset: CGVector(dx: offset(100), dy: 0.5))
        let end = slider.coordinate(withNormalizedOffset: CGVector(dx: offset(150), dy: 0.5))
        let action = Date().timeIntervalSince1970
        start.press(forDuration: 0.2, thenDragTo: end)
        let changed = waitUntil(timeout: 10) {
            let value = (slider.value as? Double) ?? (slider.value as? String).flatMap(Double.init)
            return value.map { $0 >= 135 && $0 <= 165 } ?? false
        }
        harnessRequire(changed, app, "Font multiplier slider reached approximately 150")
        let back = app.buttons["Back"].firstMatch
        harnessRequire(back.waitForExistence(timeout: 10), app, "Settings Back button")
        back.tap()
        harnessRequire(element(app, "reading-host").waitForExistence(timeout: 30), app, "Return to reading")
        let after = settledDomVerse16(app, "multiplier-after", after: action) { words in
            before.count == 4 && zip(before, words).allSatisfy { $1.width > $0.width * 1.2 }
        }
        XCTAssertEqual(after.count, 4, "All four tracked words must grow without relaunch")
        shot(app, "multiplier-after")
    }
}
