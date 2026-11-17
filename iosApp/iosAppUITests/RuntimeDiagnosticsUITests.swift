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
        XCUIDevice.shared.press(.home)
        app.activate()
        probe.tap()
        let result = element(app, "runtime-diagnostics-result")
        guard result.waitForExistence(timeout: 30) else {
            XCTFail("Probe tap did not produce a result state; button label: \(probe.label)")
            return
        }
        guard waitUntil(timeout: 30, { result.label != "running" }) else {
            XCTFail("Diagnostic report did not complete")
            return
        }
        for _ in 0..<10 {
            if result.exists && !result.label.contains("=missing") { break }
            let previous = result.label
            probe.tap()
            _ = waitUntil(timeout: 3) { result.label != previous }
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
        secondProbe.tap()
        let secondResult = element(app, "runtime-diagnostics-result")
        XCTAssertTrue(secondResult.waitForExistence(timeout: 30))
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
