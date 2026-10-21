import XCTest

/// Measurement only: do not assert a mapping until the controller has observed this Compose version at runtime.
final class DynamicTypeUITests: RealAppUITestCase {
    func testMeasureFontScaleAtXXXL() {
        let app = launch([
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryXXXL",
            "-debugFontScaleProbe"
        ])
        let probe = element(app, "ab-font-scale")
        XCTAssertTrue(probe.waitForExistence(timeout: 60), "debug font-scale probe must appear")
        attachTree(app, "font-scale-XXXL")
        let raw = probe.value as? String ?? ""
        let result = XCTAttachment(string: "category=UICTContentSizeCategoryXXXL fontScale=\(raw) label=\(probe.label)")
        result.name = "font-scale-measurement-XXXL"
        result.lifetime = .keepAlways
        add(result)
        XCTAssertNotNil(Float(raw), "probe accessibility value must expose the numeric LocalDensity fontScale")
    }
}
