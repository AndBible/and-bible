import XCTest
import SharedUi

/// Keychain needs an application identity on the simulator (a bare Kotlin/Native test binary gets
/// -34018), so the IosKeychainSecretStore cases run here, hosted by the app.
final class KeychainSelfTests: XCTestCase {
    func testKeychainSecretStoreCases() {
        if let failures = IosKeychainSelfTest.shared.run() {
            XCTFail(failures)
        }
    }
}
