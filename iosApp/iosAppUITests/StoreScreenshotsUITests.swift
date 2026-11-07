import XCTest

/// Store screenshot capture (issue #4021, `scripts/store-shots/ios-capture.sh`). Not part of the smoke or full suites'
/// assertions: it only runs when a plan file exists, written by the capture script into `tmp/store-shots/plan.json` of
/// the synced tree. The app data (demo databases and Sword modules) was seeded into the simulator's app container by
/// `scripts/store-shots/ios-seed.sh` before this test runs, so the app starts on the demo workspaces.
///
/// For every shot of the plan: launch, switch to the shot's workspace through the production Workspaces sheet, perform
/// the shot's action, and attach a full-screen screenshot named `store-<shot id>`; the capture script exports those
/// attachments as raws. A shot whose setup fails is attached as `store-FAILED-<shot id>` with its AX tree, and the test
/// fails at the end, so a partial set is never mistaken for a complete one.
final class StoreScreenshotsUITests: RealAppUITestCase {
    private struct Plan: Decodable {
        let lang: String
        let shots: [PlanShot]
    }

    private struct PlanShot: Decodable {
        let id: String
        let workspace: String
        let action: String
        let action_arg: String?
        let night: Bool
        /// Bookmark actions only (resolved from content.yaml by `storeshots plan`): verse to tap, the note's first
        /// characters, the bookmark's primary label name.
        let verse: Int?
        let note_prefix: String?
        let label: String?
    }

    /// `<repo>/tmp/store-shots/plan.json`, found from this source file's path (the simulator runner reads host paths).
    private static var planURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("tmp/store-shots/plan.json")
    }

    private func loadPlan() throws -> Plan {
        guard let data = try? Data(contentsOf: Self.planURL) else {
            throw XCTSkip("No store screenshot plan at \(Self.planURL.path) (only scripts/store-shots/ios-capture.sh runs this)")
        }
        return try JSONDecoder().decode(Plan.self, from: data)
    }

    private func label(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    private func launchStore(_ lang: String) -> XCUIApplication {
        let locale = lang.replacingOccurrences(of: "-", with: "_")
        return launch(["-AppleLanguages", "(\(lang))", "-AppleLocale", locale, "-locale_pref", lang,
                       "-UIAnimationDragCoefficient", "1"])
    }

    func testCaptureStoreShots() throws {
        let plan = try loadPlan()
        var failed: [String] = []
        for shot in plan.shots {
            if !capture(shot, lang: plan.lang) { failed.append(shot.id) }
        }
        XCTAssertTrue(failed.isEmpty, "Store shots failed: \(failed.joined(separator: ", "))")
    }

    private func fail(_ app: XCUIApplication, _ shot: PlanShot, _ why: String) -> Bool {
        attachTree(app, "store-FAILED-\(shot.id)")
        self.shot(app, "store-FAILED-\(shot.id)")
        let note = XCTAttachment(string: why); note.name = "store-FAILED-\(shot.id)-reason"; note.lifetime = .keepAlways
        add(note)
        app.terminate()
        return false
    }

    private func capture(_ shot: PlanShot, lang: String) -> Bool {
        let app = launchStore(lang)
        guard element(app, "reading-host").waitForExistence(timeout: 120) else {
            return fail(app, shot, "reading host did not appear")
        }
        guard switchWorkspace(app, shot.workspace) else { return fail(app, shot, "workspace \(shot.workspace)") }
        settle()
        guard perform(app, shot) else { return fail(app, shot, "action \(shot.action)") }
        settle()
        let image = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        image.name = "store-\(shot.id)"
        image.lifetime = .keepAlways
        add(image)
        attachTree(app, "store-\(shot.id)")
        sendToBackground(app)
        app.terminate()
        return true
    }

    /// Fonts, WebView layout and image decoding finish within a few seconds; there is no reliable "idle" signal.
    private func settle() { sleep(4) }

    /// Reading toolbar overflow "More options" -> "Workspaces…" -> the named row ("<name> (current)" when active).
    private func switchWorkspace(_ app: XCUIApplication, _ name: String) -> Bool {
        let more = label(app, "More options")
        guard more.waitForExistence(timeout: 30), more.isHittable else { return false }
        more.tap()
        let entry = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Workspaces'")).firstMatch
        guard entry.waitForExistence(timeout: 20) else { return false }
        entry.tap()
        let current = app.buttons.matching(NSPredicate(format: "label == %@", "\(name) (current)")).firstMatch
        let row = app.buttons.matching(NSPredicate(format: "label == %@", name)).firstMatch
        guard waitUntil(timeout: 20, { current.exists || row.exists }) else { return false }
        attachTree(app, "workspaces-sheet")
        if row.exists && row.isHittable {
            row.tap()
        } else {
            let close = label(app, "Close")
            guard close.exists else { return false }
            close.tap()
        }
        // The sheet's own title disappears; a workspace's name can also appear in the content (a StudyPad title).
        let sheetTitle = app.staticTexts.matching(NSPredicate(format: "label == 'Workspaces'")).firstMatch
        return waitUntil(timeout: 20) { !sheetTitle.exists && !current.exists }
    }

    /// Taps the verse number of `verse` in the reading WebView (a StaticText whose label is exactly the number,
    /// below the toolbar and on screen) and waits for the verse's bookmark popup ("Bookmark" action).
    private func openVerseMenu(_ app: XCUIApplication, _ verse: Int) -> Bool {
        let toolbarBottom = label(app, "More options").frame.maxY
        let screen = app.frame
        let numbers = app.webViews.staticTexts.matching(NSPredicate(format: "label == %@", String(verse)))
        guard waitUntil(timeout: 20, { numbers.count > 0 }) else { return false }
        for number in numbers.allElementsBoundByIndex {
            let f = number.frame
            if f.minY > toolbarBottom && f.maxY < screen.maxY - 40 && f.width > 0 {
                app.coordinate(withNormalizedOffset: .zero)
                    .withOffset(CGVector(dx: f.maxX + 12, dy: f.midY)).tap()
                return label(app, "Bookmark").waitForExistence(timeout: 15)
            }
        }
        return false
    }

    private func element(_ app: XCUIApplication, beginningWith prefix: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
    }

    private func perform(_ app: XCUIApplication, _ shot: PlanShot) -> Bool {
        switch shot.action {
        case "none":
            return true
        case "verse_menu":
            return openVerseMenu(app, shot.verse ?? 1)
        case "note":
            guard openVerseMenu(app, shot.verse ?? 1), let prefix = shot.note_prefix else { return false }
            // The card's note line opens the note view; tap its first line (the element can span the label chips).
            let note = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", prefix)).firstMatch
            guard note.waitForExistence(timeout: 15) else { return false }
            let f = note.frame
            app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: f.minX + 20, dy: f.minY + 8)).tap()
            return true
        case "label_dialog":
            guard openVerseMenu(app, shot.verse ?? 1), let name = shot.label else { return false }
            let chip = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", name)).firstMatch
            guard chip.waitForExistence(timeout: 15) else { return false }
            chip.tap()
            let edit = element(app, beginningWith: "Select or edit labels")
            guard edit.waitForExistence(timeout: 15) else { return false }
            edit.tap()
            return waitUntil(timeout: 15) { self.label(app, "Assign labels").exists }
        case "workspaces":
            label(app, "More options").tap()
            let entry = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Workspaces'")).firstMatch
            guard entry.waitForExistence(timeout: 20) else { return false }
            entry.tap()
            return app.buttons.matching(NSPredicate(format: "label ENDSWITH '(current)'")).firstMatch
                .waitForExistence(timeout: 20)
        case "book_chooser":
            // The toolbar's reference title ("Psalms 89:14") opens the book chooser sheet.
            let title = app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", ".*[0-9]+:[0-9]+")).firstMatch
            guard title.waitForExistence(timeout: 20) else { return false }
            title.tap()
            return waitUntil(timeout: 20) { self.label(app, "Gen").exists }
        case "search":
            let find = app.buttons.matching(NSPredicate(format: "label == 'Find'")).firstMatch
            guard find.waitForExistence(timeout: 20) else { return false }
            find.tap()
            // The first search of a module asks to build its index (the measured dialog has no text field yet).
            let create = label(app, "Create")
            if create.waitForExistence(timeout: 10) {
                create.tap()
                _ = waitUntil(timeout: 180) { !create.exists }
                sleep(3)
            }
            // The search field shows "Find" as its placeholder; it is not exposed as a TextField.
            let field = app.textFields.firstMatch.exists ? app.textFields.firstMatch : label(app, "Find")
            guard field.waitForExistence(timeout: 20) else { return false }
            field.tap()
            sleep(1)
            app.typeText((shot.action_arg ?? "love") + "\n")
            sleep(8)
            return true
        default:
            attachTree(app, "action-\(shot.action)-unsupported")
            return false
        }
    }
}
