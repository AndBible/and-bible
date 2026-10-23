import XCTest

/// Sync UI helpers shared by `SyncAcceptanceUITests` and `EndToEndUITests`, moved out of the former so both drive
/// the WebDAV settings screen, the pinned-certificate decision and the settings text editors identically.
/// A struct bound to the running test case (not an `XCTestCase` extension) so its names cannot collide with
/// the `RealAppUITestCase` helpers of the same name (`shot`, ...) that other classes define.
struct SyncHarness {
    let test: XCTestCase

    /// Normalizes a SHA-256 fingerprint (64 hex digits, optionally colon separated); nil when it is not one.
    func normalizedSha256(_ value: String) -> String? {
        let pattern = "^(?:[0-9A-Fa-f]{64}|(?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})$"
        guard value.range(of: pattern, options: .regularExpression) == (value.startIndex..<value.endIndex) else { return nil }
        return value.replacingOccurrences(of: ":", with: "").uppercased()
    }

    func shot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name; attachment.lifetime = .keepAlways; test.add(attachment)
    }

    func tree(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(string: app.debugDescription)
        attachment.name = name; attachment.lifetime = .keepAlways; test.add(attachment)
    }

    func require(_ condition: Bool, _ app: XCUIApplication, _ message: String,
                         sensitive: Bool = false, file: StaticString = #filePath, line: UInt = #line) {
        // A password editor must never be copied into a hierarchy attachment.
        if !condition && !sensitive { tree(app, "sync-failure-hierarchy"); shot(app, "sync-failure") }
        XCTAssertTrue(condition, message, file: file, line: line)
    }

    func label(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    func rowButton(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", text)).firstMatch
    }

    // A hittable row can still move during residual scrolling and cancel its tap gesture.
    func waitForStableFrame(_ target: XCUIElement, _ app: XCUIApplication, _ text: String) {
        var stableFrame: CGRect?
        var stableSince: TimeInterval?
        let frameStable = NSPredicate { _, _ in
            guard target.exists && target.isHittable else {
                stableFrame = nil
                stableSince = nil
                return false
            }
            let frame = target.frame
            let now = ProcessInfo.processInfo.systemUptime
            if let baseline = stableFrame, let since = stableSince,
               abs(frame.origin.x - baseline.origin.x) <= 0.5,
               abs(frame.origin.y - baseline.origin.y) <= 0.5,
               abs(frame.size.width - baseline.size.width) <= 0.5,
               abs(frame.size.height - baseline.size.height) <= 0.5 {
                return now - since >= 1.0
            }
            stableFrame = frame
            stableSince = now
            return false
        }
        require(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: frameStable, object: app)], timeout: 15) == .completed,
                app, "Settings row did not remain frame-stable and hittable for one second: \(text)")
    }

    func reveal(_ app: XCUIApplication, _ text: String, toggle: Bool = false) -> XCUIElement {
        let target = toggle
            ? app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", text)).firstMatch
            : rowButton(app, text)
        for _ in 0..<10 {
            if target.exists && target.isHittable {
                waitForStableFrame(target, app, text)
                return target
            }
            app.swipeUp()
        }
        require(target.exists && target.isHittable, app, "Required settings row not reachable: \(text)")
        waitForStableFrame(target, app, text)
        return target
    }

    /// Handles the untrusted-certificate decision only when it is shown, and only for the pinned local fixture.
    /// Returns false (after recording a failure) when the dialog is unexpected or does not match exactly;
    /// returns true when no dialog is shown or the verified certificate was trusted once.
    func trustPinnedCertificateIfShown(_ app: XCUIApplication, serverURL: String, sha256: String?) -> Bool {
        let certificateTitle = app.staticTexts.matching(
            NSPredicate(format: "label == %@", "Untrusted server certificate")).firstMatch
        guard certificateTitle.exists else { return true }
        let body = app.staticTexts.matching(NSPredicate(
            format: "label BEGINSWITH %@",
            "The certificate of localhost is not trusted by Android.")).firstMatch
        let trust = app.buttons.matching(NSPredicate(
            format: "label == %@", "Trust this certificate")).firstMatch
        guard let suppliedFingerprint = sha256,
              let expectedFingerprint = normalizedSha256(suppliedFingerprint),
              URL(string: serverURL)?.host == "localhost" else {
            require(false, app, "Certificate trust requires an explicit localhost fixture fingerprint")
            return false
        }
        let lines = body.exists ? body.label.components(separatedBy: .newlines) : []
        let hashes = lines.filter { $0.hasPrefix("SHA-256: ") }
        let certificateMatches = lines.contains("Subject: localhost")
            && lines.contains("Issuer: CN=localhost, O=AndBible disposable test")
            && hashes.count == 1
            && normalizedSha256(String(hashes[0].dropFirst("SHA-256: ".count))) == expectedFingerprint
            && trust.exists && trust.isEnabled && trust.isHittable
        require(certificateMatches, app, "Certificate identity or full SHA-256 did not match localhost fixture")
        guard certificateMatches else { return false }
        shot(app, "task7-localhost-certificate-verified")
        trust.tap()
        return true
    }

    /// Opens the real Device synchronization destination from Settings and waits for its title and backend row.
    func openDeviceSynchronization(_ app: XCUIApplication, _ failure: String) -> Bool {
        reveal(app, "Device synchronization").tap()
        let backend = rowButton(app, "Synchronization Backend")
        let destination = NSPredicate { _, _ in
            app.staticTexts.matching(NSPredicate(format: "label == %@", "Device synchronization")).firstMatch.exists
                && backend.exists && backend.isHittable
        }
        let arrived = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: destination, object: app)], timeout: 15) == .completed
        require(arrived, app, failure)
        return arrived
    }

    func edit(_ app: XCUIApplication, _ title: String, _ value: String, sensitive: Bool = false) {
        reveal(app, title).tap()
        require(label(app, title).waitForExistence(timeout: 10) && app.buttons["Apply"].firstMatch.exists, app,
                "Settings editor title or Apply action missing", sensitive: sensitive)
        // Compose exposes EditableText as TextView, including password inputs with secure IME traits.
        let editors = app.textViews
        let field = editors.firstMatch
        require(field.waitForExistence(timeout: 10), app, "Settings text editor missing", sensitive: sensitive)
        require(editors.count == 1 && field.isEnabled && field.isHittable, app,
                "Expected one enabled, hittable settings TextView editor", sensitive: sensitive)
        if !sensitive && (field.value as? String) == value {
            let cancel = app.buttons["Cancel"].firstMatch
            let canCancel = cancel.waitForExistence(timeout: 5) && cancel.isEnabled && cancel.isHittable
            require(canCancel, app, "Unchanged settings editor Cancel action unavailable")
            guard canCancel else { return }
            cancel.tap()
            let closed = NSPredicate { _, _ in !field.exists }
            let didClose = XCTWaiter.wait(
                for: [XCTNSPredicateExpectation(predicate: closed, object: app)], timeout: 10) == .completed
            require(didClose, app, "Unchanged settings editor did not close after Cancel")
            return
        }
        // Initial selection does not request focus, and a focus tap can collapse that selection.
        field.tap()
        let focused = NSPredicate(format: "hasKeyboardFocus == true")
        let focusReady = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: focused, object: field)], timeout: 10) == .completed
        require(focusReady, app, "Settings text editor did not acquire keyboard focus", sensitive: sensitive)
        guard focusReady else { return }

        // Compose 1.11.1 omits accessibilityValue for an empty EditableText.
        func isEmpty() -> Bool { field.value == nil || (field.value as? String) == "" }
        if !isEmpty() {
            // Select All is available for password inputs too; never use Copy/Cut or the clipboard.
            field.press(forDuration: 1.0)
            let selectAll = app.menuItems["Select All"].firstMatch
            let selectionReady = selectAll.waitForExistence(timeout: 5) && selectAll.isHittable
            require(selectionReady, app, "Settings editor Select All action missing", sensitive: sensitive)
            guard selectionReady else { return }
            selectAll.tap()
            field.typeText(XCUIKeyboardKey.delete.rawValue)
        }
        let cleared = isEmpty()
        require(cleared, app, "Settings editor was not fully cleared", sensitive: sensitive)
        guard cleared else { return }
        if !value.isEmpty { field.typeText(value) }
        if !sensitive {
            require((field.value as? String) == value, app, "Settings editor value did not match fixture")
        }
        let apply = app.buttons["Apply"].firstMatch
        require(apply.waitForExistence(timeout: 5), app, "Settings Apply button missing", sensitive: sensitive)
        apply.tap()
        let closed = NSPredicate { _, _ in !field.exists }
        require(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: closed, object: app)], timeout: 10) == .completed,
                app, "Settings editor did not close", sensitive: sensitive)
    }
}
