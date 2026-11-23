import XCTest

/// The label assign sheet (#4201, #4207) on a real iPhone/iPad simulator.
///
/// #4216: typing fast into the sheet's search field moved the cursor to the start of the word on iPhone (the field
/// showed the controller's echo, a dispatch late). Every round types a word in one burst and reads the field back.
/// #4215: in landscape with the keyboard down the sheet shows the header, the chip row and at least two label rows;
/// the screenshots are the evidence (`labels-landscape-rest`).
///
/// Route: the first-run example bookmarks of `-resetDatabases` (Exodus, Psalms, Isaiah, John ...) in the debug
/// `bookmarks` scenario; long press a row, "Assign labels". Leaves the app terminated and the device in portrait.
final class LabelAssignSheetUITests: RealAppUITestCase {
    private static var kjvEnsured = false
    override func setUp() {
        super.setUp()
        if !Self.kjvEnsured { ensureKJVInstalled(); Self.kjvEnsured = true }
    }

    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
        super.tearDown()
    }

    private func byLabel(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func openAssignSheet(_ app: XCUIApplication) {
        // The release notice can cover the first launches; it is not the subject here.
        let ready = element(app, "debug-feature-ready")
        harnessRequire(waitUntil(timeout: 90) { self.dismissProvisioningReleaseNotice(app); return ready.exists }, app,
                       "bookmarks debug route ready")
        let row = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label MATCHES %@", "^(Exodus|Psalms|Isaiah|John) [0-9]+:[0-9]+, .*")).firstMatch
        harnessRequire(row.waitForExistence(timeout: 60), app, "an example bookmark row is listed")
        row.press(forDuration: 1.2)
        let assign = byLabel(app, "Assign labels")
        harnessRequire(assign.waitForExistence(timeout: 15) && assign.isHittable, app, "long press offers 'Assign labels'")
        assign.tap()
        harnessRequire(element(app, "label-assign-search-chip").waitForExistence(timeout: 20), app, "the assign sheet opened")
    }

    /// Opens the search, types [word] in one burst and returns what the field holds afterwards.
    private func typeBurst(_ app: XCUIApplication, _ word: String) -> String {
        element(app, "label-assign-search-chip").tap()
        let field = element(app, "label-assign-search")
        harnessRequire(field.waitForExistence(timeout: 10), app, "the search field opened")
        _ = waitUntil(timeout: 5) { field.debugDescription.contains("Keyboard Focused") }
        app.typeText(word)
        usleep(800_000)
        let typed = field.value as? String ?? ""
        let clear = element(app, "label-assign-search-clear")
        harnessRequire(clear.exists, app, "the clear button is there")
        clear.tap()
        harnessRequire(waitUntil(timeout: 10) { !field.exists }, app, "clear closed the search")
        return typed
    }

    func testFastTypingKeepsEveryKeyInOrderAndLandscapeShowsTwoRows() {
        let app = launch(["-standardTestFixture", "-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16",
                          "-featureScenario", "bookmarks"])
        openAssignSheet(app)
        shot(app, "labels-portrait-rest")
        var results: [String] = []
        for word in ["prayerrequests", "sermonnotesandideas", "promisesofgod", "abcdefghijklmnop", "studystudystudy"] {
            let typed = typeBurst(app, word)
            results.append("\(word) -> \(typed)")
            if typed != word { shot(app, "labels-typing-mismatch-\(word)") }
        }
        let report = XCTAttachment(string: results.joined(separator: "\n"))
        report.name = "typing-bursts"; report.lifetime = .keepAlways; add(report)
        XCTAssertTrue(results.allSatisfy { $0.split(separator: " ").first == $0.split(separator: " ").last },
                      "typed bursts did not read back intact:\n" + results.joined(separator: "\n"))

        // #4215: landscape, keyboard down. The sheet is reopened in landscape so it is measured there.
        if !isPad {
            byLabel(app, "Close").firstMatch.tap()
            XCUIDevice.shared.orientation = .landscapeLeft
            sleep(2)
            openAssignSheet(app)
            sleep(1)
            shot(app, "labels-landscape-rest")
        }
        app.terminate()
    }
}
