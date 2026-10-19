import XCTest
import UIKit
import MessageUI
import UniformTypeIdentifiers
import SharedUi

/// These cases require the app's UIApplication scene, not a standalone Kotlin/Native test binary.
@MainActor
final class ReportLifecycleTests: XCTestCase {
    private var window: UIWindow!
    private var previousKeyWindow: UIWindow?
    private var host: UIViewController!
    private var probe: IosReportLifecycleProbe!
    private var requestFinished: XCTestExpectation?

    override func setUp() async throws {
        try await super.setUp()
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        host = UIViewController()
        window = UIWindow(windowScene: scene)
        window.rootViewController = host
        window.makeKeyAndVisible()
        host.loadViewIfNeeded()
        probe = IosReportLifecycleProbe()
    }

    override func tearDown() async throws {
        // On assertion failure, still cancel the real request and let UIKit dismiss naturally.
        // Never delete its files here: a broken lifetime must remain a test failure.
        if let probe, probe.started, !probe.completed {
            probe.cancel()
            if let requestFinished { await fulfillment(of: [requestFinished], timeout: 10) }
        }
        requestFinished = nil
        if let host, host.presentedViewController != nil {
            await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                host.dismiss(animated: true) { continuation.resume() }
            }
        }
        window?.isHidden = true
        window?.rootViewController = nil
        previousKeyWindow?.makeKeyAndVisible()
        probe = nil
        host = nil
        window = nil
        try await super.tearDown()
    }

    func testReportCancellationAfterPresentationKeepsFilesThroughDismissal() async throws {
        try await reportRequest(cancel: true)
    }

    func testReportDoubleTerminalKeepsFilesThroughDismissal() async throws {
        try await reportRequest(cancel: false)
    }

    private func reportRequest(cancel: Bool) async throws {
        // Never open a configured Mail composer or perform any send action.
        try XCTSkipIf(MFMailComposeViewController.canSendMail(), "Requires simulator without Mail account")
        let appeared = expectation(description: "Real animated presentation completed")
        let completed = expectation(description: "Actual send coroutine completed")
        requestFinished = XCTestExpectation(description: "Failure-path request cleanup")
        let sheet = LifecycleSheet()
        var attachment: URL?
        var observedDismissal = false
        // An unrelated request sentinel detects over-broad cleanup, without relying on a global temp snapshot.
        let sentinel = FileManager.default.temporaryDirectory.appendingPathComponent("andbible-report-sentinel-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: sentinel, withIntermediateDirectories: false)
        defer { try? FileManager.default.removeItem(at: sentinel) }
        sheet.appeared = { appeared.fulfill() }
        sheet.disappearing = { [self] in
            guard let attachment else { return XCTFail("No exact staged attachment") }
            XCTAssertTrue(FileManager.default.fileExists(atPath: attachment.path))
            XCTAssertFalse(probe.completed, "Send must wait for dismissal/release")
            observedDismissal = true
        }
        probe.startReport(host: host, sheet: sheet, staged: { path in
            attachment = URL(fileURLWithPath: path)
        }, completion: { failure in
            XCTAssertNil(failure)
            completed.fulfill()
            self.requestFinished?.fulfill()
        })
        await fulfillment(of: [appeared], timeout: 10)
        XCTAssertTrue(host.presentedViewController === sheet)
        XCTAssertNil(sheet.transitionCoordinator)
        let file = try XCTUnwrap(attachment)
        XCTAssertEqual(try String(contentsOf: file, encoding: .utf8), "payload")
        let requestDirectory = file.deletingLastPathComponent()
        XCTAssertTrue(requestDirectory.lastPathComponent.hasPrefix("andbible-report-"))
        if cancel { probe.cancel() } else { probe.finishTwice() }
        // Cancellation is dispatched independently onto Main; files cannot be deleted at cancel().
        XCTAssertTrue(FileManager.default.fileExists(atPath: file.path))
        await fulfillment(of: [completed], timeout: 10)
        XCTAssertTrue(observedDismissal)
        XCTAssertEqual(probe.cancelled, cancel)
        XCTAssertNil(host.presentedViewController)
        XCTAssertNil(sheet.presentingViewController)
        XCTAssertFalse(FileManager.default.fileExists(atPath: requestDirectory.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: sentinel.path))
    }

    func testDoubleTerminalReleasesExactlyOnceAfterRealDismissal() async throws {
        let sheet = LifecycleSheet()
        let appeared = expectation(description: "Presented")
        let completed = expectation(description: "Resumed after release")
        requestFinished = XCTestExpectation(description: "Failure-path request cleanup")
        sheet.appeared = { appeared.fulfill() }
        sheet.disappearing = { [self] in
            XCTAssertEqual(probe.releases, 0)
            XCTAssertFalse(probe.completed)
        }
        probe.startPresentation(host: host, sheet: sheet) { failure in
            XCTAssertNil(failure)
            completed.fulfill()
            self.requestFinished?.fulfill()
        }
        await fulfillment(of: [appeared], timeout: 10)
        XCTAssertTrue(host.presentedViewController === sheet)
        probe.finishTwice()
        await fulfillment(of: [completed], timeout: 10)
        XCTAssertEqual(probe.releases, 1)
        XCTAssertNil(host.presentedViewController)
        probe.finishTwice()
        XCTAssertEqual(probe.releases, 1)
    }

    func testAlertCancellationPreservesUIKitOwnedDelegate() async throws {
        let alert = UIAlertController(title: "Test", message: "No report sent", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        let originalDelegate = alert.presentationController?.delegate
        let completed = expectation(description: "Cancelled alert released")
        requestFinished = XCTestExpectation(description: "Failure-path request cleanup")
        probe.startPresentation(host: host, sheet: alert) { failure in
            XCTAssertNil(failure)
            completed.fulfill()
            self.requestFinished?.fulfill()
        }
        // UIKit's transition completion is the boundary before the real Job.cancel(), never a delay.
        let presented = expectation(description: "Alert presentation transition completed")
        if let transition = alert.transitionCoordinator {
            XCTAssertTrue(transition.animate(alongsideTransition: nil) { _ in presented.fulfill() })
        } else {
            XCTFail("Expected a real animated alert transition")
            return
        }
        await fulfillment(of: [presented], timeout: 10)
        XCTAssertTrue(host.presentedViewController === alert)
        XCTAssertTrue(alert.presentationController?.delegate === originalDelegate)
        XCTAssertFalse(probe.completed)
        probe.cancel()
        await fulfillment(of: [completed], timeout: 10)
        XCTAssertTrue(probe.cancelled)
        XCTAssertEqual(probe.releases, 1)
        XCTAssertNil(host.presentedViewController)
        XCTAssertTrue(alert.presentationController?.delegate === originalDelegate)
    }

    func testConsecutiveDocumentPresentationsResumeAfterDismissal() async throws {
        for _ in 0..<2 {
            probe = IosReportLifecycleProbe()
            let sheet = UIDocumentPickerViewController(forOpeningContentTypes: [.data], asCopy: true)
            let completed = expectation(description: "Document picker released")
            requestFinished = XCTestExpectation(description: "Failure-path request cleanup")
            probe.startPresentation(host: host, sheet: sheet) { failure in
                XCTAssertNil(failure)
                completed.fulfill()
                self.requestFinished?.fulfill()
            }
            let presented = expectation(description: "Document picker presentation completed")
            if let transition = sheet.transitionCoordinator {
                XCTAssertTrue(transition.animate(alongsideTransition: nil) { _ in presented.fulfill() })
            } else {
                XCTFail("Expected a real animated document picker transition")
                return
            }
            await fulfillment(of: [presented], timeout: 10)
            XCTAssertTrue(host.presentedViewController === sheet)
            probe.finishTwice()
            await fulfillment(of: [completed], timeout: 10)
            XCTAssertEqual(probe.releases, 1)
            XCTAssertNil(host.presentedViewController)
            XCTAssertNil(sheet.presentingViewController)
        }
    }
}

@MainActor
private final class LifecycleSheet: UIViewController {
    var appeared: (() -> Void)?
    var disappearing: (() -> Void)?
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if let transition = transitionCoordinator {
            transition.animate(alongsideTransition: nil) { [weak self] _ in self?.appeared?() }
        } else {
            appeared?()
        }
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        disappearing?()
    }
}
