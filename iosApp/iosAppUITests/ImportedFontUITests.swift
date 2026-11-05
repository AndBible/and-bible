import XCTest

/// Exercises the production incoming installer, font chooser and WKWebView, then restarts without importing again.
final class ImportedFontUITests: RealAppUITestCase {
    override func setUp() {
        super.setUp()
        ensureKJVInstalled()
    }

    private func tapLabel(_ app: XCUIApplication, _ label: String) {
        let target = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
        for _ in 0..<6 {
            if target.waitForExistence(timeout: 2) && target.isHittable { break }
            // iOS omits the labels of offscreen Compose choices from AX. Scroll inside the sheet,
            // not the dimmed reading screen, before requiring the exact imported family.
            let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
            let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.52))
            start.press(forDuration: 0.1, thenDragTo: end)
        }
        if !target.exists || !target.isHittable { attachTree(app, "missing-\(label)"); shot(app, "missing-\(label)") }
        XCTAssertTrue(target.exists && target.isHittable, "Missing or not hittable: \(label)")
        if label == "StudyMono" { shot(app, "imported-font-offered") }
        target.tap()
    }

    private func openFonts(_ app: XCUIApplication) {
        tapLabel(app, "More options")
        tapLabel(app, "All text options …")
        // The shared settings controller uses the static row title; the selected family is a separate value.
        tapLabel(app, "Font family")
    }

    private func assertRenderedFont(_ app: XCUIApplication, _ step: String) {
        var evidence = ""
        let rendered = waitUntil(timeout: 40) {
            let probe = self.element(app, "reading-dom-probe")
            guard probe.exists, let json = probe.value as? String, let data = json.data(using: .utf8),
                  let report = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let started = report["started"] as? TimeInterval,
                  abs(Date().timeIntervalSince1970 - started) < 10,
                  let family = report["fontFamily"] as? String,
                  let faces = report["fontFaces"] as? [[String: Any]],
                  let words = report["verse16"] as? [[String: Any]], words.count == 4 else { return false }
            evidence = json
            let widths = words.compactMap { $0["width"] as? Double }
            // DejaVu Sans Mono is genuinely loaded, not merely a CSS family falling back to the system font.
            return family.contains("StudyMono") && faces.contains { ($0["family"] as? String)?.contains("StudyMono") == true && $0["status"] as? String == "loaded" }
                && widths.count == 4 && abs(widths[0] - widths[1]) < 0.1 && abs(widths[0] - widths[3]) > 2
        }
        let attachment = XCTAttachment(string: evidence)
        attachment.name = "font-dom-\(step)"; attachment.lifetime = .keepAlways; add(attachment)
        shot(app, "font-rendered-\(step)")
        XCTAssertTrue(rendered, "Imported font must be loaded and rendered after \(step)")
    }

    func testImportedFontIsOfferedRenderedAndPersists() {
        var app = launch(["-standardTestFixture", "-resetDatabases", "-incomingRuntimeTest", "-incomingRemoveFixtures", "-incomingFixture", "font",
                          "-readingAccessibilityProbe", "-openRef", "John.3.16"])
        let confirmation = app.staticTexts["Do you want to install module (StudyMono.ttf)?"].firstMatch
        XCTAssertTrue(confirmation.waitForExistence(timeout: 60), "Production font install confirmation")
        tapLabel(app, "OK")
        XCTAssertTrue(element(app, "incoming-cold-fixtures-installed").waitForExistence(timeout: 90), "Production installer registered the imported font")
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 90))
        openFonts(app)
        tapLabel(app, "StudyMono")
        shot(app, "imported-font-selected")
        for _ in 0..<4 {
            let back = app.buttons["Back"].firstMatch
            if back.exists && back.isHittable { back.tap() } else { break }
        }
        assertRenderedFont(app, "selection")
        XCUIDevice.shared.press(.home)
        app.terminate()

        app = launch(["-standardTestFixture", "-readingAccessibilityProbe"])
        defer { app.terminate() }
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 90))
        assertRenderedFont(app, "relaunch")
        openFonts(app)
        tapLabel(app, "StudyMono") // Persistence was independently verified in the reading DOM before reopening the chooser.
        shot(app, "font-choice-after-relaunch")
    }
}
