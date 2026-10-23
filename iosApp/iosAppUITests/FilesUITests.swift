import XCTest
import UIKit

/// App-hosted production native-port proof only. These are NOT verse-share or bookmark-CSV E2E tests.
/// Includes Save/write-back and image-provider payload checks; incoming install has separate tests.
/// H/R feature navigation is outside this native-port suite.
/// Harness contract: -filesPortTest selects the actual production port with its default presenter;
/// files-port-open triggers it and files-port-idle returns only after the presentation has ended.
final class FilesUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
        addUIInterruptionMonitor(withDescription: "Notification permission only") { alert in
            guard alert.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] 'notifications'")).count > 0,
                  alert.buttons["Don't Allow"].exists else { return false }
            alert.buttons["Don't Allow"].tap()
            return true
        }
    }

    private func dismissNotificationPermissionIfPresent() {
        // SpringBoard alerts are absent from the app-only accessibility hierarchy.
        let alert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if alert.waitForExistence(timeout: 5),
           alert.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] 'notifications'")).count > 0,
           alert.buttons["Don't Allow"].exists {
            alert.buttons["Don't Allow"].tap()
        }
    }

    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    private func launchPort(_ port: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-task6ResetCrashState", "-standardTestFixture", "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-filesPortTest", port, "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        dismissNotificationPermissionIfPresent()
        XCTAssertTrue(element(app, "files-port-idle").waitForExistence(timeout: 60), "Debug port harness not ready")
        XCTAssertTrue(element(app, "files-port-open").isHittable, "Production-port trigger is not usable")
        return app
    }

    private func evidence(_ app: XCUIApplication, _ name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = "tree-\(name)"
        tree.lifetime = .keepAlways
        add(tree)
    }

    private func waitUntil(_ message: String, app: XCUIApplication, evidenceName: String, _ condition: @escaping () -> Bool) {
        let predicate = NSPredicate { _, _ in condition() }
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: nil)
        let result = XCTWaiter.wait(for: [expectation], timeout: 20)
        evidence(app, "\(evidenceName)-\(result == .completed ? "ready" : "timeout")")
        XCTAssertEqual(result, .completed, message)
    }

    private func waitForSystemControl(_ control: XCUIElement, app: XCUIApplication, name: String, message: String) {
        let found = control.waitForExistence(timeout: 20)
        evidence(app, "\(name)-\(found ? "visible" : "timeout")")
        XCTAssertTrue(found, message)
    }

    private func open(_ app: XCUIApplication) {
        let trigger = element(app, "files-port-open")
        XCTAssertTrue(trigger.isHittable)
        trigger.tap()
    }

    private func assertReturned(_ app: XCUIApplication, systemControl: XCUIElement) {
        waitUntil("System presentation did not disappear", app: app, evidenceName: "system-dismissed") { !systemControl.exists }
        waitForSystemControl(element(app, "files-port-idle"), app: app, name: "port-returned", message: "Port did not finish after dismissal")
        XCTAssertTrue(element(app, "files-port-open").isHittable, "App is still covered by a system presentation")
    }

    func testProductionDocumentPickerVisibleCancelAndRepresent() {
        let app = launchPort("document")
        defer { app.terminate() }
        for pass in 1...2 {
            open(app)
            // These are system Files controls, not a harness-owned success label.
            let cancel = app.buttons["Cancel"].firstMatch
            waitForSystemControl(cancel, app: app, name: "document-\(pass)", message: "System document picker did not appear")
            XCTAssertTrue(cancel.isHittable, "Document picker is not visibly interactive")
            waitUntil("No Files browser is attached", app: app, evidenceName: "document-content-\(pass)") {
                if UIDevice.current.userInterfaceIdiom == .pad {
                    let picker = app.otherElements["Browse View (Picker)"].firstMatch
                    let sidebar = picker.navigationBars["DOCSidebarView"].firstMatch
                    let recents = picker.cells["DOC.sidebar.item.Recents"].firstMatch
                    return picker.exists && sidebar.exists && recents.exists && recents.isHittable
                }
                return app.navigationBars["Browse"].exists || app.buttons["Browse"].exists || app.navigationBars["Recents"].exists
            }
            cancel.tap()
            assertReturned(app, systemControl: cancel)
        }
    }

    func testProductionTextShareVisibleDismissAndRepresent() {
        let app = launchPort("share")
        defer { app.terminate() }
        for pass in 1...2 {
            open(app)
            let activity = app.otherElements["ActivityListView"].firstMatch
            let copy = activity.cells.matching(NSPredicate(format: "identifier == 'actionGroupCell' AND label == 'Copy'")).firstMatch
            waitForSystemControl(copy, app: app, name: "share-\(pass)", message: "Actual activity sheet actions did not appear")
            XCTAssertTrue(copy.isHittable, "Share sheet is not visibly interactive (including iPad popover)")
            let close = app.buttons["Close"].firstMatch
            if close.exists && close.isHittable {
                close.tap()
            } else if UIDevice.current.userInterfaceIdiom == .pad {
                // The production popover is centred; an outside tap cancels without choosing an activity.
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.03, dy: 0.15)).tap()
            } else {
                // Interactive dismissal, not a harness calling UIKit dismiss() for the test.
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.55))
                    .press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95)))
            }
            assertReturned(app, systemControl: copy)
        }
    }

    func testProductionImagePickerVisibleCancelAndRepresent() {
        let app = launchPort("image")
        defer { app.terminate() }
        for pass in 1...2 {
            open(app)
            let cancel = app.buttons["Cancel"].firstMatch
            waitForSystemControl(cancel, app: app, name: "image-\(pass)", message: "Actual PHPicker did not appear")
            XCTAssertTrue(cancel.isHittable, "Image picker is not visibly interactive")
            XCTAssertTrue(app.buttons["Photos"].exists || app.staticTexts["Photos"].exists || app.navigationBars["Photos"].exists,
                          "No system Photos picker content is attached")
            cancel.tap()
            assertReturned(app, systemControl: cancel)
        }
    }

    // Folder selection must use actual IosDocumentPicker.openDocumentTree. The returned folder ref,
    // not Documents or an injected filesystem URL, is the independent destination oracle for both modes.
    private func localRoot(_ app: XCUIApplication) -> XCUIElement {
        // Files can retain the previous folder in its root accessibility identifier after navigating up.
        return app.otherElements.matching(NSPredicate(format:
            "identifier BEGINSWITH 'DOC.browsingRoot Source: com.apple.FileProvider.LocalStorage,'")).firstMatch
    }

    private func selectCreatedFolder(_ app: XCUIApplication, folderName: String) {
        let select = element(app, "files-port-folder-select")
        waitForSystemControl(select, app: app, name: "folder-select-ready", message: "Harness has no genuine folder-selection phase")
        XCTAssertTrue(select.isHittable)
        select.tap()
        navigateToLocalRoot(app)
        let root = localRoot(app)
        if UIDevice.current.userInterfaceIdiom == .pad {
            let newFolders = root.navigationBars["FullDocumentManagerViewControllerNavigationBar"].buttons.matching(identifier: "New Folder")
            waitForSystemControl(newFolders.firstMatch, app: app, name: "folder-picker-new", message: "Folder picker cannot create local folder")
            XCTAssertEqual(newFolders.count, 1, "Cannot uniquely identify the observed iPad New Folder toolbar button")
            guard newFolders.count == 1 else { return }
            XCTAssertTrue(newFolders.firstMatch.isHittable)
            newFolders.firstMatch.tap()
        } else {
            let more = root.navigationBars["FullDocumentManagerViewControllerNavigationBar"].buttons["More"].firstMatch
            waitForSystemControl(more, app: app, name: "folder-picker-more", message: "Folder picker has no local More control")
            XCTAssertTrue(more.isHittable)
            more.tap()
            let newFolder = app.descendants(matching: .any).matching(NSPredicate(format: "label == 'New Folder'")).firstMatch
            waitForSystemControl(newFolder, app: app, name: "folder-picker-new", message: "Folder picker cannot create local folder")
            XCTAssertTrue(newFolder.isHittable)
            newFolder.tap()
        }
        let rename = root.textViews["DOC.inlineRenameField"].firstMatch
        waitForSystemControl(rename, app: app, name: "folder-picker-rename", message: "Folder picker has no inline rename")
        XCTAssertTrue(rename.isHittable)
        rename.typeText(folderName)
        evidence(app, "folder-picker-exact-name")
        XCTAssertEqual(rename.value as? String, folderName)
        let done = app.buttons["Done"].firstMatch
        XCTAssertTrue(done.isHittable && done.isEnabled)
        done.tap()
        waitUntil("Folder rename did not finish", app: app, evidenceName: "folder-picker-renamed") { !rename.exists }
        openNamedLocalFolder(app, folderName: folderName)
        // 'Open' is the expected folder-picker confirmation; its actual selector still needs runtime evidence.
        let confirm = app.buttons["Open"].firstMatch
        waitForSystemControl(confirm, app: app, name: "folder-picker-confirm", message: "System folder picker has no Open confirmation")
        XCTAssertTrue(confirm.isHittable && confirm.isEnabled)
        confirm.tap()
        waitUntil("System folder picker did not dismiss", app: app, evidenceName: "folder-picker-dismissed") { !confirm.exists }
        waitForSystemControl(element(app, "files-port-folder-ready"), app: app, name: "folder-ref-ready",
                             message: "Harness did not retain actual selected folder DocumentRef")
    }

    private func navigateToLocalRoot(_ app: XCUIApplication) {
        let root = localRoot(app)
        let localName = UIDevice.current.userInterfaceIdiom == .pad ? "On My iPad" : "On My iPhone"
        let navigation = root.navigationBars["FullDocumentManagerViewControllerNavigationBar"]
        let rootTitle = navigation.staticTexts.matching(NSPredicate(format:
            "label == %@ OR label == %@", localName, "\(localName), Actions Menu")).firstMatch
        if rootTitle.exists { return }
        let parent = navigation.buttons[localName].firstMatch
        if parent.exists && parent.isHittable {
            parent.tap()
            waitUntil("Local provider parent navigation did not reach root", app: app, evidenceName: "save-parent-root") {
                rootTitle.exists
            }
            return
        }
        let local: XCUIElement
        if UIDevice.current.userInterfaceIdiom == .pad {
            // iPad exposes a persistent sidebar, which may attach after the picker itself.
            local = app.cells["DOC.sidebar.item.On My iPad"].firstMatch
        } else {
            local = app.cells.matching(NSPredicate(format: "label == %@", localName)).firstMatch
            if !local.exists {
                let browse = app.buttons["Browse"].firstMatch
                waitForSystemControl(browse, app: app, name: "save-browse", message: "Cannot select a local Files location")
                XCTAssertTrue(browse.isHittable)
                browse.tap()
            }
        }
        waitForSystemControl(local, app: app, name: "save-local", message: "Local Files location is unavailable")
        XCTAssertTrue(local.isHittable)
        local.tap()
        waitUntil("Local provider root was not attached", app: app, evidenceName: "save-local-root") {
            root.exists && rootTitle.exists
        }
    }

    private func openNamedLocalFolder(_ app: XCUIApplication, folderName: String) {
        let opened = localRoot(app).navigationBars["FullDocumentManagerViewControllerNavigationBar"]
            .buttons.matching(NSPredicate(format: "label == %@", "\(folderName), Actions Menu")).firstMatch
        if !opened.exists {
            let folder = localRoot(app).cells.matching(identifier: "\(folderName), Folder").firstMatch
            let grid = localRoot(app).collectionViews["File View"].firstMatch
            waitForSystemControl(grid, app: app, name: "save-folder-grid", message: "Actual Files folder grid is missing")
            // "On My iPhone" can hold dozens of leftover folders and a blind full-height swipeDown at the list top pulls
            // the picker sheet down (the grid then vanishes). Find the uniquely named folder through the picker's own
            // Search field first; only fall back to bounded in-grid drags.
            var foundBySearch = false
            if !(folder.exists && folder.isHittable) {
                let search = app.searchFields.firstMatch
                if search.exists && search.isHittable {
                    search.tap()
                    search.typeText(folderName)
                    foundBySearch = folder.waitForExistence(timeout: 15)
                    evidence(app, "save-folder-search-\(foundBySearch ? "found" : "empty")")
                    if !foundBySearch {
                        let cancel = app.buttons["Cancel"].firstMatch
                        if cancel.exists && cancel.isHittable { cancel.tap() }
                        _ = grid.waitForExistence(timeout: 10)
                    }
                }
            }
            if foundBySearch {
                XCTAssertEqual(localRoot(app).cells.matching(identifier: "\(folderName), Folder").count, 1,
                               "Cannot uniquely identify the exact UI-created folder in the search results")
                XCTAssertTrue(waitForStableOnScreen(app, folder, timeout: 20), "Searched folder cell did not settle")
                evidence(app, "save-unique-folder-settled")
                folder.tap()
                waitForSystemControl(opened, app: app, name: "save-exact-folder-open", message: "Picker did not open the exact selected folder")
                XCTAssertTrue(opened.isHittable, "Exact folder navigation title is not interactive")
                return
            }
            // Fallback: bounded drags that start and end inside the grid, well below its top edge. Scroll down first
            // (reveals lower items), then back up in the middle of the grid; the grid must survive every drag.
            func drag(up: Bool) -> Bool {
                let frame = grid.frame
                let upper = frame.minY + frame.height * 0.45, lower = frame.minY + frame.height * 0.85
                let x = frame.midX
                let high = app.coordinate(withNormalizedOffset: CGVector(dx: x / app.frame.width, dy: upper / app.frame.height))
                let low = app.coordinate(withNormalizedOffset: CGVector(dx: x / app.frame.width, dy: lower / app.frame.height))
                (up ? low : high).press(forDuration: 0.1, thenDragTo: up ? high : low)
                usleep(700_000)
                if !grid.exists {
                    evidence(app, "save-folder-grid-vanished")
                    XCTFail("The 'File View' grid vanished after a bounded drag (the picker sheet was probably dismissed)")
                    return false
                }
                return true
            }
            for _ in 0..<24 {
                if folder.exists && folder.isHittable { break }
                if !drag(up: true) { return }
            }
            for _ in 0..<12 {
                if folder.exists && folder.isHittable { break }
                if !drag(up: false) { return }
            }
            evidence(app, "save-unique-folder-scrolled")
            let matches = localRoot(app).cells.matching(identifier: "\(folderName), Folder")
            XCTAssertEqual(matches.count, 1, "Cannot uniquely identify the exact UI-created folder")
            guard matches.count == 1 else { return }
            var previousSnapshot: [String] = []
            var stableObservations = 0
            var observations: [String] = []
            let started = Date()
            let settled = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                let unique = matches.count == 1
                let hittable = unique && folder.isHittable
                do {
                    let gridSnapshot = try grid.snapshot()
                    func visibleCells(_ node: XCUIElementSnapshot) -> [String] {
                        let row = node.elementType == .cell && node.frame.intersects(gridSnapshot.frame)
                            ? ["\(node.identifier)|\(NSCoder.string(for: node.frame))"] : []
                        return row + node.children.flatMap { visibleCells($0) }
                    }
                    let snapshot = visibleCells(gridSnapshot) + ["grid|\(NSCoder.string(for: gridSnapshot.frame))"]
                    let changed = snapshot != previousSnapshot
                    stableObservations = unique && hittable && !changed ? stableObservations + 1 : 0
                    observations.append("index=\(observations.count + 1) elapsed=\(Date().timeIntervalSince(started)) unique=\(unique) hittable=\(hittable) changed=\(changed) diff=\(changed ? snapshot.description : "none")")
                    previousSnapshot = snapshot
                    return stableObservations >= 2
                } catch {
                    stableObservations = 0
                    previousSnapshot = []
                    observations.append("index=\(observations.count + 1) elapsed=\(Date().timeIntervalSince(started)) snapshotError=\(error)")
                    return false
                }
            }, object: nil)
            let settledResult = XCTWaiter.wait(for: [settled], timeout: 20)
            let diagnostics = XCTAttachment(string: observations.joined(separator: "\n"))
            diagnostics.name = "save-folder-stability-observations"
            diagnostics.lifetime = .keepAlways
            add(diagnostics)
            evidence(app, "save-unique-folder-settled")
            XCTAssertEqual(settledResult, .completed, "Exact folder/grid did not settle before tapping")
            guard settledResult == .completed else { return }
            folder.tap()
        }
        waitForSystemControl(opened, app: app, name: "save-exact-folder-open", message: "Picker did not open the exact selected folder")
        XCTAssertTrue(opened.isHittable, "Exact folder navigation title is not interactive")
    }

    private func saveToLocalTestFolder(_ app: XCUIApplication, filename: String, folderName: String, confirmation: String) {
        let save = app.buttons[confirmation].firstMatch
        waitForSystemControl(save, app: app, name: "save-picker", message: "Actual system \(confirmation) picker did not appear")
        XCTAssertTrue(save.isHittable)
        // Do not trust the provider's remembered last folder. Navigate and verify the exact unique name.
        navigateToLocalRoot(app)
        openNamedLocalFolder(app, folderName: folderName)
        evidence(app, "save-destination-before-confirm")
        let fields = app.textFields.allElementsBoundByIndex.filter { $0.isHittable }
        XCTAssertEqual(fields.count, 1, "Cannot uniquely identify the actual filename editor")
        guard fields.count == 1 else { return }
        fields[0].tap()
        evidence(app, "save-filename-editing")
        // Files edits the stem separately; the expected full filename remains the payload/path oracle.
        let stem = (filename as NSString).deletingPathExtension
        XCTAssertEqual(fields[0].value as? String, stem,
                       "Actual filename editor does not contain the complete expected stem")
        XCTAssertTrue(save.isHittable)
        save.tap()
        assertReturned(app, systemControl: save)
        waitForSystemControl(element(app, "files-port-verified"), app: app, name: "saved-content-verified",
                             message: "Actual saved destination bytes were not verified")
        XCTAssertFalse(element(app, "files-port-error").exists)
    }

    private func launchSavePort(_ mode: String, filename: String, reset: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-task6ResetCrashState", "-standardTestFixture", "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-filesPortTest", mode, "-filesPortFilename", filename,
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        if reset { app.launchArguments += ["-resetData"] }
        app.launch()
        dismissNotificationPermissionIfPresent()
        waitForSystemControl(element(app, "files-port-idle"), app: app, name: "save-harness-ready",
                             message: "Save/write-back harness not ready")
        return app
    }

    func testCreateDocumentSaveWritesReturnedDestination() {
        let filename = "create-writeback-\(UUID().uuidString).csv"
        let app = launchSavePort("create-writeback", filename: filename)
        defer { app.terminate() }
        let folderName = "AndBible Save \(UUID().uuidString)"
        selectCreatedFolder(app, folderName: folderName)
        open(app)
        saveToLocalTestFolder(app, filename: filename, folderName: folderName,
                              confirmation: UIDevice.current.userInterfaceIdiom == .pad ? "Save" : "Move")
    }

    func testFileDeliverySavePreservesPayload() {
        let filename = "delivery-save-\(UUID().uuidString).txt"
        let app = launchSavePort("delivery-save", filename: filename)
        defer { app.terminate() }
        let folderName = "AndBible Save \(UUID().uuidString)"
        selectCreatedFolder(app, folderName: folderName)
        open(app)
        saveToLocalTestFolder(app, filename: filename, folderName: folderName, confirmation: "Save")
    }

    /// OS-routing provisioning contract (not an incoming-queue injection):
    /// - New debug modes os-routing-zip / os-routing-epub export the bundled deterministic SWORD ZIP /
    ///   two-chapter EPUB under the exact filesPortFilename through actual IosFileDelivery Save.
    /// - Existing folder selection, idle/open/verified tags retain their real native/byte-proof meaning.
    /// - Provisioning must preflight the real backend as absent, never offer/install the fixture.
    /// - Future genuine Swift -> IosPlatformBootstrap receipt witness must identify this exact filename;
    ///   it must survive a cold OS launch without replaying resetData or synthesizing an incoming offer.
    /// Cold ZIP and warm EPUB both use observed real Files Share -> AndBible Dev, exact production
    /// confirmation/OK, then require genuine receipt and registered backend witnesses. Cold needs a new
    /// app process; warm needs the same live process. Export/Files launch alone proves no routing.
    private func probeOsFilesHandoff(mode: String, fileExtension: String, cold: Bool) {
        let token = UUID().uuidString
        let filename = "F-OS-\(token).\(fileExtension)"
        let folderName = "AndBible OS \(token)"
        let app = launchSavePort(mode, filename: filename, reset: true)
        let files = XCUIApplication(bundleIdentifier: "com.apple.DocumentsApp")
        defer { files.terminate(); app.terminate() }
        selectCreatedFolder(app, folderName: folderName)
        open(app)
        saveToLocalTestFolder(app, filename: filename, folderName: folderName, confirmation: "Save")
        XCTAssertFalse(element(app, "os-routing-receipt").exists,
                       "Export provisioning must not simulate a Swift incoming URL receipt")
        XCTAssertFalse(element(app, "os-routing-content-verified").exists,
                       "Provisioning must not install the exported fixture")
        if cold {
            app.terminate()
            XCTAssertEqual(app.state, .notRunning, "Cold handoff requires a terminated app")
        }
        files.launch()
        XCTAssertTrue(files.wait(for: .runningForeground, timeout: 20), "Actual Files app did not launch")
        evidence(files, "os-files-\(cold ? "cold" : "warm")-\(filename)-hierarchy-probe")
        if cold {
            XCTAssertEqual(app.state, .notRunning, "No genuine file-open action has occurred in this probe")
        } else {
            XCTAssertTrue(app.state == .runningBackground || app.state == .runningBackgroundSuspended,
                          "Warm handoff requires the provisioned app process to remain alive")
        }
        do {
            let recents = files.otherElements.matching(identifier:
                "DOC.browsingRoot Source: com.apple.DocumentManager.RecentDocuments, Title: Recents").firstMatch
            if !recents.exists {
                let tabs = files.tabBars["DOC.browsingModeTabBar"].buttons.matching(identifier: "Recents")
                waitForSystemControl(tabs.firstMatch, app: files, name: "os-files-recents-tab",
                                     message: "Observed Files Recents tab is unavailable")
                XCTAssertEqual(tabs.count, 1, "Cannot uniquely identify the Files Recents tab")
                guard tabs.count == 1 else { return }
                XCTAssertTrue(tabs.firstMatch.isHittable)
                guard tabs.firstMatch.isHittable else { return }
                tabs.firstMatch.tap()
            }
            let collections = recents.collectionViews.matching(NSPredicate(format:
                "identifier == 'File View' OR identifier == 'DOCItemCollectionViewIdentifier'"))
            let collection = collections.firstMatch
            waitForSystemControl(collection, app: files, name: "os-files-recents-grid",
                                 message: "Observed actual Files Recents collection is unavailable")
            XCTAssertEqual(collections.count, 1, "Cannot uniquely identify the observed Files Recents collection")
            XCTAssertTrue(collection.isHittable)
            guard collections.count == 1 && collection.isHittable else { return }
            let candidates = collection.cells.containing(.staticText, identifier: filename)
            waitForSystemControl(candidates.firstMatch, app: files, name: "os-files-exact-nonce",
                                 message: "Exact exported nonce file is absent from Files Recents")
            XCTAssertEqual(candidates.count, 1, "Cannot uniquely identify the exported nonce file")
            guard candidates.count == 1 else { return }
            let exactFile = candidates.firstMatch
            let labels = exactFile.staticTexts.matching(NSPredicate(format: "label == %@", filename))
            XCTAssertEqual(labels.count, 1, "File cell must contain the exact full nonce filename")
            XCTAssertTrue(exactFile.isHittable, "Exact exported nonce ZIP is not interactive")
            guard labels.count == 1 && exactFile.isHittable else { return }
            exactFile.press(forDuration: 1)
            evidence(files, "os-files-exact-nonce-context-menu-probe")
            let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            evidence(springboard, "os-files-context-menu-springboard-probe")
            if cold {
                XCTAssertEqual(app.state, .notRunning, "Context-menu probe must not open AndBible")
            } else {
                XCTAssertTrue(app.state == .runningBackground || app.state == .runningBackgroundSuspended,
                              "Context-menu probe must not foreground the warm AndBible process")
            }
            let shares = files.buttons.matching(NSPredicate(format: "label == 'Share'"))
            waitForSystemControl(shares.firstMatch, app: files, name: "os-files-context-share",
                                 message: "Observed exact Files Share action is unavailable")
            XCTAssertEqual(shares.count, 1, "Cannot uniquely identify the observed Files Share action")
            guard shares.count == 1 else { return }
            let share = shares.firstMatch
            XCTAssertTrue(share.isHittable && share.isEnabled, "Exact Files Share action is not usable")
            guard share.isHittable && share.isEnabled else { return }
            share.tap()
            let sheet = files.otherElements["shareSheet.activity.contentView"].firstMatch
            let targets = sheet.collectionViews["activityCollectionView"].cells.matching(NSPredicate(format:
                "identifier == 'shareCell' AND label == 'AndBible Dev'"))
            let header = files.navigationBars["UIActivityContentView"].otherElements.matching(NSPredicate(format:
                "label == %@", (filename as NSString).deletingPathExtension))
            waitUntil("Exact nonce share sheet/AndBible target is not ready", app: files,
                      evidenceName: "os-files-exact-target-ready") {
                sheet.exists && targets.count == 1 && targets.firstMatch.isHittable &&
                    targets.firstMatch.isEnabled && header.count == 1
            }
            XCTAssertEqual(targets.count, 1)
            XCTAssertEqual(header.count, 1, "Share sheet must describe the exact nonce file")
            guard targets.count == 1 && header.count == 1 &&
                targets.firstMatch.isHittable && targets.firstMatch.isEnabled else { return }
            evidence(files, "os-files-share-target-before-tap")
            evidence(springboard, "os-files-share-target-springboard-before-tap")
            if cold {
                XCTAssertEqual(app.state, .notRunning, "AndBible must remain cold until the real target tap")
            } else {
                XCTAssertTrue(app.state == .runningBackground || app.state == .runningBackgroundSuspended,
                              "Warm AndBible must remain alive in background until the real target tap")
            }
            targets.firstMatch.tap()
            XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30),
                          "Actual Files target did not bring AndBible to foreground")
            let confirmation = app.staticTexts.matching(NSPredicate(format: "label == %@",
                "Do you want to install module (\(filename))?"))
            waitForSystemControl(confirmation.firstMatch, app: app, name: "os-files-production-confirmation",
                                 message: "Genuine Files handoff did not show the exact nonce installation confirmation")
            XCTAssertEqual(confirmation.count, 1)
            let confirmationScrolls = app.scrollViews.containing(.staticText, identifier:
                "Do you want to install module (\(filename))?")
            XCTAssertEqual(confirmationScrolls.count, 1, "Cannot uniquely scope the observed production confirmation")
            guard confirmationScrolls.count == 1 else { return }
            let okayButtons = confirmationScrolls.firstMatch.buttons.matching(NSPredicate(format: "label == 'OK'"))
            XCTAssertEqual(okayButtons.count, 1, "Production confirmation must expose one exact OK button")
            guard okayButtons.count == 1 else { return }
            let okay = okayButtons.firstMatch
            XCTAssertTrue(okay.isHittable && okay.isEnabled)
            guard okay.isHittable && okay.isEnabled else { return }
            okay.tap()
            // Required debug witness values (not labels): export-process is the UUID of the process
            // that completed real Save; receipt is filename|count=1|process=UUID. Cold OS launch must
            // retain the export expectation but receive a new process UUID; warm keeps the same process.
            // Neither tag may forge ingress.
            let receipt = element(app, "os-routing-receipt")
            waitForSystemControl(receipt, app: app, name: "os-files-genuine-bootstrap-receipt",
                                 message: "Missing genuine bootstrap receipt witness after real Files target and production OK")
            let exportedProcess = element(app, "os-routing-export-process")
            waitForSystemControl(exportedProcess, app: app, name: "os-files-export-process",
                                 message: "Missing persisted real-export process witness")
            let receiptValue = receipt.value as? String ?? ""
            let prefix = "\(filename)|count=1|process="
            XCTAssertTrue(receiptValue.hasPrefix(prefix), "Receipt must identify the exact nonce and one genuine arrival")
            let process = receiptValue.hasPrefix(prefix) ? String(receiptValue.dropFirst(prefix.count)) : ""
            let exportProcess = exportedProcess.value as? String ?? ""
            XCTAssertNotNil(UUID(uuidString: process), "Receipt process token must be an actual UUID")
            XCTAssertNotNil(UUID(uuidString: exportProcess), "Export process token must be an actual UUID")
            if cold {
                XCTAssertNotEqual(process, exportProcess, "Cold Files handoff must create a different app process")
            } else {
                XCTAssertEqual(process, exportProcess, "Warm Files handoff must reach the same live app process")
            }
            let content = element(app, "os-routing-content-verified")
            waitUntil("Actual registered backend content is not verified for this receipt", app: app,
                      evidenceName: "os-files-real-backend-content") {
                content.exists && content.value as? String == filename
            }
            XCTAssertFalse(element(app, "files-port-error").exists)
            evidence(files, "os-files-after-real-target")
            evidence(springboard, "os-files-after-real-target-springboard")
        }
        // Warm EPUB reuses the observed Files controls; any EPUB-specific difference must fail visibly.
    }

    func testColdOsFilesZipProvisioningAndHierarchyProbe() {
        probeOsFilesHandoff(mode: "os-routing-zip", fileExtension: "zip", cold: true)
    }

    func testWarmOsFilesEpubHandoffAndBackendProof() {
        probeOsFilesHandoff(mode: "os-routing-epub", fileExtension: "epub", cold: false)
    }

    func testImageSelectionCopiesExpectedProviderAsset() throws {
        // The controller preloads the generated recognizable PNG using simctl addmedia and supplies its
        // observed, unique PHPicker accessibility label. Never choose the first/arbitrary photo or skip.
        // Fixture contract: 16x16 pixels; opaque red/green/blue/yellow quadrants, no orientation metadata.
        // Harness uses the default production picker/provider, waits for dismissal, decodes the returned
        // absolute file URL and checks all quadrant centres/dimensions before files-port-verified appears.
        let label = try XCTUnwrap(ProcessInfo.processInfo.environment["FILES_TEST_PHOTO_LABEL"],
                                  "Controller must provide the deterministic fixture asset's exact Photos label")
        XCTAssertFalse(label.isEmpty)
        let app = launchPort("image-select")
        defer { app.terminate() }
        open(app)
        let cancel = app.buttons["Cancel"].firstMatch
        waitForSystemControl(cancel, app: app, name: "image-select-picker", message: "Actual PHPicker did not appear")
        let asset = app.images.matching(NSPredicate(format: "label == %@", label)).firstMatch
        waitForSystemControl(asset, app: app, name: "image-expected-asset", message: "Recognizable fixture asset not found in Photos")
        XCTAssertEqual(app.images.matching(NSPredicate(format: "label == %@", label)).count, 1,
                       "Fixture asset label must identify exactly one photo")
        // Let XCTest resolve this exact element's hit point; no coordinate or ancestor fallback.
        asset.tap()
        assertReturned(app, systemControl: cancel)
        waitForSystemControl(element(app, "files-port-verified"), app: app, name: "image-provider-content-verified",
                             message: "Returned provider copy did not match the expected dimensions and pixel pattern after dismissal")
        XCTAssertFalse(element(app, "files-port-error").exists)
    }

    /// Provisioning diagnostic only: no save/result assertion or feature-parity claim.
    /// Creates a unique folder through the observed system inline rename controls; never guesses its filesystem path.
    func testLocalCreatePickerReportsNewFolderAvailability() {
        let app = launchSavePort("create-writeback", filename: "provider-diagnostic-\(UUID().uuidString).csv")
        defer { app.terminate() }
        let select = element(app, "files-port-folder-select")
        waitForSystemControl(select, app: app, name: "provider-folder-select", message: "Genuine folder picker trigger is unavailable")
        XCTAssertTrue(select.isHittable)
        select.tap()
        let confirm = app.buttons["Open"].firstMatch
        waitForSystemControl(confirm, app: app, name: "provider-folder-picker", message: "Actual folder picker Open control did not appear")
        XCTAssertTrue(confirm.isHittable)
        navigateToLocalRoot(app)
        let root = localRoot(app)
        waitForSystemControl(root, app: app, name: "provider-local-root", message: "Actual local provider root was not attached")
        let newFolder: XCUIElement
        if UIDevice.current.userInterfaceIdiom == .pad {
            let matches = root.navigationBars["FullDocumentManagerViewControllerNavigationBar"].buttons
                .matching(identifier: "New Folder")
            waitForSystemControl(matches.firstMatch, app: app, name: "provider-local-new-folder",
                                 message: "Local Files New Folder toolbar button is unavailable")
            XCTAssertEqual(matches.count, 1, "Cannot uniquely identify the observed iPad New Folder toolbar button")
            guard matches.count == 1 else { return }
            newFolder = matches.firstMatch
        } else {
            let more = root.navigationBars["FullDocumentManagerViewControllerNavigationBar"].buttons["More"].firstMatch
            waitForSystemControl(more, app: app, name: "provider-local-more", message: "Local Files ellipsis menu is unavailable")
            XCTAssertTrue(more.isHittable)
            more.tap()
            newFolder = app.descendants(matching: .any).matching(NSPredicate(format: "label == 'New Folder'")).firstMatch
        }
        let available = newFolder.waitForExistence(timeout: 10)
        evidence(app, "provider-local-menu-new-folder-\(available ? "present" : "absent")")
        XCTAssertTrue(available, "Local Files provider does not expose New Folder; save provisioning remains blocked")
        XCTAssertTrue(newFolder.isHittable, "Local provider New Folder action is not usable")
        newFolder.tap()
        let nameField = localRoot(app).textViews["DOC.inlineRenameField"].firstMatch
        waitForSystemControl(nameField, app: app, name: "provider-new-folder-inline-rename", message: "System inline folder rename field was not presented")
        XCTAssertTrue(nameField.isHittable)
        let folderName = "AndBible UI Probe \(UUID().uuidString)"
        // New Folder starts with its default name selected, as observed in the real Files UI.
        nameField.typeText(folderName)
        evidence(app, "provider-new-folder-name-entered")
        XCTAssertEqual(nameField.value as? String, folderName, "System folder name entry was not exact")
        let done = app.buttons["Done"].firstMatch
        waitForSystemControl(done, app: app, name: "provider-new-folder-done", message: "System inline rename keyboard has no Done action")
        XCTAssertTrue(done.isHittable && done.isEnabled)
        done.tap()
        waitUntil("Inline folder rename did not finish", app: app, evidenceName: "provider-new-folder-created") { !nameField.exists }
        // Verify the exact UUID cell and opened title, never a stale provider root title or label prefix.
        openNamedLocalFolder(app, folderName: folderName)
    }
}
