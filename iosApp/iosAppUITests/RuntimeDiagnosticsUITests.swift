import XCTest

final class RuntimeDiagnosticsUITests: RealAppUITestCase {
    func testReportContainsPersistedLifecycleAcrossRelaunch() throws {
        #if !DEBUG
        throw XCTSkip("Runtime diagnostics proof requires a debug test build")
        #else
        ensureKJVInstalled()
        let args = ["-standardTestFixture", "-runtimeDiagnosticsTest", "-openDocument", "KJV", "-openRef", "John.3.16"]
        let app = launch(args)
        let probe = element(app, "runtime-diagnostics-probe")
        XCTAssertTrue(waitUntil(timeout: 90) {
            self.dismissProvisioningReleaseNotice(app)
            return probe.exists
        })
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 90))
        XCTAssertTrue(john3Shown(app, timeout: 90), "KJV reading pane must load before lifecycle probe")
        let baselineAppFrame = app.frame
        let baselineProbeFrame = probe.frame
        XCUIDevice.shared.press(.home)
        app.activate()
        func waitForReportCompletion(_ result: XCUIElement, request: Int) -> Bool {
            let finished = waitUntil(timeout: 30) {
                guard result.exists else { return false }
                let label = result.label
                return label.split(separator: " ").contains("request=\(request)"[...]) &&
                    (label.hasPrefix("attachment=") || label.hasPrefix("error "))
            }
            guard finished else {
                XCTFail("Diagnostic report did not complete")
                return false
            }
            guard result.label.hasPrefix("attachment=") else {
                XCTFail("Diagnostic report failed")
                return false
            }
            return true
        }
        func tapReportProbe(restoringGeometry: Bool = false) -> Bool {
            let button = element(app, "runtime-diagnostics-probe")
            var target = CGRect.zero
            var appFrame = CGRect.zero
            func matches(_ actual: CGRect, _ expected: CGRect) -> Bool {
                abs(actual.minX - expected.minX) < 0.5 && abs(actual.minY - expected.minY) < 0.5 &&
                    abs(actual.width - expected.width) < 0.5 && abs(actual.height - expected.height) < 0.5
            }
            guard waitUntil(timeout: 10, {
                guard button.exists, button.isHittable else { return false }
                target = button.frame
                appFrame = app.frame
                return !target.isEmpty && !appFrame.isEmpty && appFrame.contains(target) &&
                    (!restoringGeometry || (matches(appFrame, baselineAppFrame) && matches(target, baselineProbeFrame)))
            }) else {
                XCTFail("Diagnostic probe must restore its hittable geometry inside the app")
                return false
            }
            // Use one coherent geometry sample, not an AX activation point sampled during the app transition.
            let tapAppFrame = restoringGeometry ? baselineAppFrame : appFrame
            let tapTarget = restoringGeometry ? baselineProbeFrame : target
            app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(
                dx: tapTarget.midX - tapAppFrame.minX, dy: tapTarget.midY - tapAppFrame.minY
            )).tap()
            return true
        }
        guard tapReportProbe(restoringGeometry: true) else { return }
        let result = element(app, "runtime-diagnostics-result")
        guard result.waitForExistence(timeout: 30) else {
            XCTFail("Probe tap did not produce a result state; button label: \(probe.label)")
            return
        }
        var request = 1
        guard waitForReportCompletion(result, request: request) else { return }
        for _ in 0..<10 {
            if !result.label.contains("=missing") { break }
            request += 1
            guard tapReportProbe() else { return }
            guard waitForReportCompletion(result, request: request) else { return }
        }
        XCTAssertFalse(result.label.contains("probe=missing"), "Settled lifecycle probe must be recorded")
        let first = result.label
        guard let firstRun = first.split(separator: " ").first(where: { $0.hasPrefix("run=") })?.dropFirst(4), !firstRun.isEmpty else {
            XCTFail("Missing first run identity: \(first)")
            return
        }
        func field(_ text: String, _ name: String) -> String? {
            text.split(separator: " ").first(where: { $0.hasPrefix(name + "=") }).map { String($0.dropFirst(name.count + 1)) }
        }
        var priorRecords: [String] = []
        for kind in ["host", "pane", "paneowner", "probe", "background", "foreground", "active", "hostactive"] {
            guard let sequence = field(first, kind), sequence != "missing" else {
                XCTFail("Missing \(kind) event: \(first)")
                return
            }
            priorRecords.append("\(firstRun):\(sequence)")
        }
        XCTAssertTrue(first.contains("attachment=true"), first)
        XCUIDevice.shared.press(.home)
        app.terminate()
        app.launch()
        let secondProbe = element(app, "runtime-diagnostics-probe")
        XCTAssertTrue(waitUntil(timeout: 90) {
            self.dismissProvisioningReleaseNotice(app)
            return secondProbe.exists
        })
        guard tapReportProbe() else { return }
        let secondResult = element(app, "runtime-diagnostics-result")
        guard waitForReportCompletion(secondResult, request: 1) else { return }
        let second = secondResult.label
        XCTAssertTrue(second.contains("runs=2+"), second)
        XCTAssertTrue(second.contains("current=true"), second)
        let retained = Set((field(second, "records") ?? "").split(separator: ",").map(String.init))
        for record in priorRecords {
            XCTAssertTrue(retained.contains(record), "Missing prior event \(record): \(second)")
        }
        guard let secondRun = second.split(separator: " ").first(where: { $0.hasPrefix("run=") })?.dropFirst(4) else {
            XCTFail("Missing second run identity: \(second)")
            return
        }
        XCTAssertNotEqual(firstRun, secondRun)
        XCTAssertTrue(second.contains("\(firstRun):"), second)
        XCTAssertTrue(second.contains("attachment=true"), second)
        XCUIDevice.shared.press(.home)
        app.terminate()
        #endif
    }
}
