import XCTest
import UIKit
import SharedUi

/// App-hosted production-port proof, not the H/R-owned copy-verse feature flow.
/// The Kotlin entry must use IosClipboardText, check the system value as well as read(),
/// and restore all previous UIPasteboard.items in finally, including on assertion failure.
final class FilesPortSelfTests: XCTestCase {
    @MainActor
    func testProductionClipboardRoundTripRestoresPreviousItems() {
        let previousItems = UIPasteboard.general.items
        // A second safety net protects the simulator clipboard if the Kotlin test fails.
        defer { UIPasteboard.general.items = previousItems }

        if let failure = IosPlatformBootstrap.shared.runFilesClipboardSelfTest() {
            XCTFail(failure)
        }

        XCTAssertEqual(
            NSArray(array: UIPasteboard.general.items),
            NSArray(array: previousItems),
            "The production clipboard self-test must restore every previous item, not only its string"
        )
    }
}
