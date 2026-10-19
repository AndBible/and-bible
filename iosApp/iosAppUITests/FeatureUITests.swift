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
        for _ in 0..<12 where !finnish.isHittable { app.swipeUp() }
        if !finnish.exists || !finnish.isHittable {
            shot(app, "settings-language-no-finnish-choice")
            attachTree(app, "settings-language-no-finnish-choice")
        }
        XCTAssertTrue(finnish.exists && finnish.isHittable)
        finnish.tap()
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

    func testManageLabelsShowsIcons() {
        let app = freshReading(scenario: "labels")
        XCTAssertTrue(app.staticTexts["Feature star"].firstMatch.waitForExistence(timeout: 30))
        XCTAssertTrue(app.staticTexts["Feature default"].firstMatch.exists)
        XCTAssertTrue(element(app, "label-glyph-star").exists, "Custom glyph node must use the actual resource painter")
        XCTAssertTrue(element(app, "label-glyph-default").exists, "Default glyph node must use the actual resource painter")
        shot(app, "manage-labels-real-glyphs")
    }

    func testDownloadAboutDialog() {
        let app = freshReading(scenario: "download")
        // The unfiltered multi-language catalogue is enormous: 15 swipes only reached ARP/ASV.
        // Narrow it through the real language picker before looking for the actual module row.
        let allLanguages = app.staticTexts.matching(NSPredicate(format: "label == 'All'")).firstMatch
        XCTAssertTrue(allLanguages.waitForExistence(timeout: 30))
        allLanguages.tap()
        // The real iOS OutlinedTextField is exported as a TextView in the picker AX tree.
        let languageSearch = app.textViews.firstMatch
        let searchReady = languageSearch.waitForExistence(timeout: 10) && languageSearch.isHittable
        if !searchReady {
            shot(app, "download-language-search-not-hittable")
            attachTree(app, "download-language-search-not-hittable")
        }
        XCTAssertTrue(searchReady, "Language picker must expose its actual hittable editable TextView")
        languageSearch.tap()
        languageSearch.typeText("English")
        let typedEnglish = waitUntil(timeout: 10) { (languageSearch.value as? String) == "English" }
        if !typedEnglish {
            shot(app, "download-language-search-input-mismatch")
            attachTree(app, "download-language-search-input-mismatch")
        }
        XCTAssertTrue(typedEnglish, "Read back the actual typed language query before selecting an option")
        let englishLanguage = app.staticTexts.matching(NSPredicate(format: "label == 'English'")).firstMatch
        let englishReady = englishLanguage.waitForExistence(timeout: 10) && englishLanguage.isHittable
        if !englishReady {
            shot(app, "download-language-no-english-option")
            attachTree(app, "download-language-no-english-option")
        }
        XCTAssertTrue(englishReady)
        englishLanguage.tap()
        let dismissed = waitUntil(timeout: 10) { !languageSearch.exists && !allLanguages.exists }
        if !dismissed {
            shot(app, "download-language-picker-not-dismissed")
            attachTree(app, "download-language-picker-not-dismissed")
        }
        XCTAssertTrue(dismissed, "Language selection must dismiss the picker and filter the catalogue")
        let kjv = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'KJV '")).firstMatch
        // The long list must settle after each bounded nudge; direction reverses if the row is passed.
        let kjvSettled = bringIntoBand(app, kjv, band: 0.2...0.8, maxSteps: 80)
        if !kjvSettled {
            shot(app, "download-english-no-kjv")
            attachTree(app, "download-english-no-kjv")
        }
        XCTAssertTrue(kjvSettled, "Real repository KJV module required, settled and hittable in the English catalogue")
        let kjvRows = app.buttons.containing(.staticText, identifier: kjv.label)
        let kjvRow = kjvRows.firstMatch
        let kjvRowReady = kjvRows.count == 1 && kjvRow.isHittable
        if !kjvRowReady {
            shot(app, "download-kjv-parent-not-hittable")
            attachTree(app, "download-kjv-parent-not-hittable")
        }
        XCTAssertTrue(kjvRowReady, "Long press must target the unique actual KJV row Button")
        kjvRow.press(forDuration: 1)
        // Long press opens the real selection toolbar, not a context menu.
        let selectionCancel = app.buttons.matching(NSPredicate(format: "label == 'Cancel'")).firstMatch
        let selectionOpened = waitUntil(timeout: 10) {
            selectionCancel.exists && selectionCancel.isHittable && app.staticTexts["1"].exists
        }
        if !selectionOpened {
            shot(app, "download-kjv-selection-not-opened")
            attachTree(app, "download-kjv-selection-not-opened")
        }
        XCTAssertTrue(selectionOpened, "One KJV document must be selected before About is available")
        let about = app.buttons["About"].firstMatch
        let aboutReady = about.waitForExistence(timeout: 10) && about.isHittable
        if !aboutReady {
            shot(app, "download-selection-about-not-hittable")
            attachTree(app, "download-selection-about-not-hittable")
        }
        XCTAssertTrue(aboutReady)
        about.tap()
        let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        let modalOpened = ok.waitForExistence(timeout: 15) && ok.isHittable
        shot(app, "download-post-about")
        attachTree(app, "download-post-about")
        XCTAssertTrue(modalOpened, "Actual About modal must open before checking metadata; background rows are not proof")
        let metadata = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS 'King James'")).firstMatch
        XCTAssertTrue(metadata.waitForExistence(timeout: 15), "About must show metadata for the selected real module")
        let osis = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS 'OSIS ID: KJV'")).firstMatch
        let identifiesModule = osis.waitForExistence(timeout: 10)
        if !identifiesModule {
            shot(app, "download-about-no-osis")
            attachTree(app, "download-about-no-osis")
        }
        XCTAssertTrue(identifiesModule, "About must identify the actual KJV module, not just a background row")
        shot(app, "download-real-kjv-about")
        ok.tap()
    }
}
