import XCTest

final class RuntimeDiagnosticsUITests: RealAppUITestCase {
    func testReportContainsPersistedLifecycleAcrossRelaunch() {
        ensureKJVInstalled()
        let args = ["-standardTestFixture", "-runtimeDiagnosticsTest", "-openDocument", "KJV", "-openRef", "John.3.16"]
        let app = launch(args)
        let probe = element(app, "runtime-diagnostics-probe")
        XCTAssertTrue(probe.waitForExistence(timeout: 90))
        XCUIDevice.shared.press(.home)
        app.activate()
        probe.tap()
        let result = element(app, "runtime-diagnostics-result")
        XCTAssertTrue(result.waitForExistence(timeout: 30))
        let first = result.label
        guard let firstRun = first.split(separator: " ").first(where: { $0.hasPrefix("run=") })?.dropFirst(4), !firstRun.isEmpty else {
            XCTFail("Missing first run identity: \(first)")
            return
        }
        XCTAssertTrue(first.contains("host=true"), first)
        XCTAssertTrue(first.contains("pane=true"), first)
        XCTAssertTrue(first.contains("probe=true"), first)
        XCTAssertTrue(first.contains("background=true"), first)
        XCTAssertTrue(first.contains("attachment=true"), first)
        XCUIDevice.shared.press(.home)
        app.terminate()
        app.launch()
        let secondProbe = element(app, "runtime-diagnostics-probe")
        XCTAssertTrue(secondProbe.waitForExistence(timeout: 90))
        secondProbe.tap()
        let secondResult = element(app, "runtime-diagnostics-result")
        XCTAssertTrue(secondResult.waitForExistence(timeout: 30))
        let second = secondResult.label
        XCTAssertTrue(second.contains("runs=2+"), second)
        XCTAssertTrue(second.contains("prior=true"), second)
        XCTAssertTrue(second.contains("current=true"), second)
        guard let secondRun = second.split(separator: " ").first(where: { $0.hasPrefix("run=") })?.dropFirst(4) else {
            XCTFail("Missing second run identity: \(second)")
            return
        }
        XCTAssertNotEqual(firstRun, secondRun)
        XCTAssertTrue(second.contains(String(firstRun)), second)
        XCTAssertTrue(second.contains("attachment=true"), second)
        XCUIDevice.shared.press(.home)
        app.terminate()
    }
}
