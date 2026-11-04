import XCTest

/// Real AppHosts screens, not the PoC harness. The finite debug navigation seam is temporary until stream R joins.
final class FeatureUITests: RealAppUITestCase {
    func testSettingsLanguageSwitch() {
        let app = freshReading(scenario: "plan-settings")
        // Daily reading offers the real selector when no plan is selected.
        let english = app.staticTexts["1-Year through Bible"].firstMatch
        XCTAssertTrue(english.waitForExistence(timeout: 30))
        english.tap()
        let settings = element(app, "debug-open-settings")
        XCTAssertTrue(settings.waitForExistence(timeout: 30), "Selected daily plan must load before switching language")
        shot(app, "loaded-plan-before-language")
        let englishTitle = app.buttons.matching(NSPredicate(format: "label == '1-Year through Bible'")).firstMatch
        let englishLoaded = englishTitle.waitForExistence(timeout: 10)
        if !englishLoaded { attachTree(app, "loaded-plan-no-english-title") }
        XCTAssertTrue(englishLoaded, "The loaded plan must expose its exact English title before switching language")
        settings.tap()
        let keepScreen = app.switches.matching(NSPredicate(format: "label CONTAINS 'Keep screen on'")).firstMatch
        for _ in 0..<8 where !keepScreen.isHittable { app.swipeUp() }
        if !keepScreen.exists || !keepScreen.isHittable {
            shot(app, "settings-switch-not-hittable")
            attachTree(app, "settings-switch-not-hittable")
        }
        XCTAssertTrue(keepScreen.exists && keepScreen.isHittable)
        // Compose exports checked state through the selected AX trait, not a value string.
        let originalSelected = keepScreen.isSelected
        let expectedSelected = !originalSelected
        keepScreen.tap()
        let changed = waitUntil(timeout: 10) { keepScreen.isSelected == expectedSelected }
        if !changed {
            shot(app, "settings-switch-not-changed")
            attachTree(app, "settings-switch-not-changed")
        }
        XCTAssertTrue(changed, "The setting must change to its explicit opposite before testing retention")
        let retainedSelected = keepScreen.isSelected
        XCTAssertEqual(retainedSelected, expectedSelected)
        XCTAssertNotEqual(retainedSelected, originalSelected)
        // The actual actionable AX node is the Button containing this exact child label.
        let languageRows = app.buttons.containing(.staticText, identifier: "Application language")
        let languageRow = languageRows.firstMatch
        let languageRowReady = bringIntoBand(app, languageRow, band: 0.2...0.75) && languageRows.count == 1
        shot(app, "settings-language-before-tap")
        attachTree(app, "settings-language-before-tap")
        XCTAssertTrue(languageRowReady, "Application language row must be settled, unique and hittable mid-screen before tapping")
        languageRow.tap()
        // Do not scroll the underlying Settings list when the editor failed to open.
        let editorClose = app.buttons.matching(NSPredicate(format: "label == 'Close'")).firstMatch
        let editorOpened = editorClose.waitForExistence(timeout: 10) && editorClose.isHittable
        if !editorOpened {
            shot(app, "settings-language-editor-not-opened")
            attachTree(app, "settings-language-editor-not-opened")
        }
        XCTAssertTrue(editorOpened, "Actual language editor must open before searching its choices")
        let finnish = app.staticTexts["fi: Suomi"].firstMatch
        // The sheet list is long and lazily laid out; settle the real row inside the sheet (y 0.36..1.0) before tapping.
        let finnishReady = bringIntoBand(app, finnish, band: 0.5...0.9, maxSteps: 40)
        let finnishRows = app.buttons.containing(.staticText, identifier: "fi: Suomi")
        let finnishRow = finnishRows.firstMatch
        if !finnishReady || finnishRows.count != 1 || !finnishRow.isHittable {
            shot(app, "settings-language-no-finnish-choice")
            attachTree(app, "settings-language-no-finnish-choice")
        }
        XCTAssertTrue(finnishReady && finnishRows.count == 1 && finnishRow.isHittable, "Unique settled hittable fi: Suomi row required")
        finnishRow.tap()
        XCTAssertTrue(app.staticTexts["Sovelluksen asetukset"].firstMatch.waitForExistence(timeout: 15), "Settings must refresh in the same process")
        let retainedSwitch = app.switches.matching(NSPredicate(format: "label CONTAINS 'Pidä näyttö päällä'")).firstMatch
        for _ in 0..<12 where !retainedSwitch.isHittable { app.swipeDown() }
        if !retainedSwitch.exists || retainedSwitch.isSelected != retainedSelected {
            shot(app, "settings-fi-switch-not-retained")
            attachTree(app, "settings-fi-switch-not-retained")
        }
        XCTAssertTrue(retainedSwitch.exists)
        XCTAssertEqual(retainedSwitch.isSelected, retainedSelected, "Language refresh must retain the edited setting")
        shot(app, "language-fi-settings")
        element(app, "debug-feature-back").tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label == 'Vuodessa läpi Raamatun'")).firstMatch.waitForExistence(timeout: 20), "The actual loaded-plan title button must refresh without restarting")
        XCTAssertFalse(englishTitle.exists)
        shot(app, "language-fi-loaded-plan")
    }

}
