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
        let englishLoaded = english.waitForExistence(timeout: 10)
        if !englishLoaded { attachTree(app, "loaded-plan-no-english-title") }
        XCTAssertTrue(englishLoaded, "The loaded plan must expose its exact English title before switching language")
        settings.tap()
        let keepScreen = app.switches.matching(NSPredicate(format: "label CONTAINS 'Keep screen on'")).firstMatch
        for _ in 0..<8 where !keepScreen.isHittable { app.swipeUp() }
        XCTAssertTrue(keepScreen.exists)
        let originalValue = keepScreen.value as? String
        XCTAssertNotNil(originalValue)
        keepScreen.tap()
        XCTAssertTrue(waitUntil(timeout: 10) {
            guard let value = keepScreen.value as? String else { return false }
            return value != originalValue
        }, "The setting must actually change before testing retention")
        let retainedValue = keepScreen.value as? String
        XCTAssertNotNil(retainedValue)
        XCTAssertNotEqual(retainedValue, originalValue)
        let language = app.staticTexts["Application language"].firstMatch
        for _ in 0..<8 where !language.isHittable { app.swipeUp() }
        XCTAssertTrue(language.waitForExistence(timeout: 10))
        language.tap()
        let finnish = app.staticTexts["fi: Suomi"].firstMatch
        for _ in 0..<12 where !finnish.isHittable { app.swipeUp() }
        XCTAssertTrue(finnish.exists)
        finnish.tap()
        XCTAssertTrue(app.staticTexts["Sovelluksen asetukset"].firstMatch.waitForExistence(timeout: 15), "Settings must refresh in the same process")
        let retainedSwitch = app.switches.matching(NSPredicate(format: "label CONTAINS 'Pidä näyttö päällä'")).firstMatch
        for _ in 0..<12 where !retainedSwitch.isHittable { app.swipeDown() }
        XCTAssertTrue(retainedSwitch.exists)
        XCTAssertEqual(retainedSwitch.value as? String, retainedValue, "Language refresh must retain the edited setting")
        shot(app, "language-fi-settings")
        element(app, "debug-feature-back").tap()
        XCTAssertTrue(app.staticTexts["Vuodessa läpi Raamatun"].firstMatch.waitForExistence(timeout: 20), "The already loaded plan name must refresh without restarting")
        XCTAssertFalse(app.staticTexts["1-Year through Bible"].exists)
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
        let languageSearch = app.textFields.firstMatch
        XCTAssertTrue(languageSearch.waitForExistence(timeout: 10), "Language picker must expose its real search field")
        languageSearch.tap()
        languageSearch.typeText("English")
        let englishLanguage = app.staticTexts.matching(NSPredicate(format: "label == 'English'")).firstMatch
        XCTAssertTrue(englishLanguage.waitForExistence(timeout: 10))
        englishLanguage.tap()
        XCTAssertTrue(waitUntil(timeout: 10) { !allLanguages.exists }, "Language selection must dismiss the picker and filter the catalogue")
        let kjv = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'KJV '")).firstMatch
        for _ in 0..<40 where !kjv.isHittable { app.swipeUp() }
        if !kjv.exists || !kjv.isHittable {
            shot(app, "download-english-no-kjv")
            attachTree(app, "download-english-no-kjv")
        }
        XCTAssertTrue(kjv.exists && kjv.isHittable, "Real repository KJV module required in the English catalogue")
        kjv.press(forDuration: 1)
        let about = app.buttons["About"].firstMatch
        XCTAssertTrue(about.waitForExistence(timeout: 10))
        about.tap()
        let metadata = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS 'King James'")).firstMatch
        XCTAssertTrue(metadata.waitForExistence(timeout: 15), "About must show metadata for the selected real module")
        XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS 'OSIS ID: KJV'")).firstMatch.waitForExistence(timeout: 10), "About must identify the actual KJV module, not just a background row")
        shot(app, "download-real-kjv-about")
        let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        XCTAssertTrue(ok.exists)
        ok.tap()
    }
}
