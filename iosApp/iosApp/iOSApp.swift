import SwiftUI
import SharedUi
import os

@main
struct iOSApp: App {
    init() {
        // macOS 26 shows an iOS app's NSLog text as <private>; log the app's own lines publicly instead
        // (Console.app / `log show --predicate 'subsystem == "org.andbible.ios"'`).
        IosPlatformBootstrap.shared.installLogSink { appLog($0) }
        IosPlatformBootstrap.shared.registerBackgroundTasks()
        #if DEBUG
        IosPlatformBootstrap.shared.prepareFilesPortTestLaunch()
        #endif
    }

    /// Full-screen reading hides the status bar: the Kotlin side posts this notification (see IosStatusBar.kt).
    @State private var statusBarHidden = false

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .statusBarHidden(statusBarHidden)
                .onReceive(NotificationCenter.default.publisher(for: Notification.Name("net.bible.andbible.statusBarHidden"))) {
                    statusBarHidden = ($0.userInfo?["hidden"] as? Bool) ?? false
                }
                .onOpenURL { IosPlatformBootstrap.shared.openIncomingFile(url: $0.absoluteString) }
        }
    }
}

/// The app's own unified-log lines, public (macOS 26 shows an iOS app's NSLog text as <private>).
private let appLogger = Logger(subsystem: "org.andbible.ios", category: "app")

func appLog(_ line: String) {
    appLogger.log("\(line, privacy: .public)")
}

/// Bridges the shared Compose UI (a Kotlin `UIViewController` exported by the SharedUi
/// framework) into SwiftUI. `MainViewControllerKt.MainViewController()` is the Kotlin
/// top-level `MainViewController()` in `sharedUi/iosMain`; it shows the AndBible I0 PoC app.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let compose = MainViewControllerKt.MainViewController()
        // iPhone and iPad keep the Compose controller as it is; only the iOS app on a Mac gets the window-controls host.
        return ProcessInfo.processInfo.isiOSAppOnMac ? MacWindowControlsHost(compose: compose) : compose
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// The iOS app on a Mac (macOS 26) reserves a top safe-area band for the window controls (traffic lights), which the
/// Compose top bars padded as an empty strip. This container measures its own safe area plainly and adapted
/// horizontally around the controls' corner, and lets the Kotlin side (`IosWindowControls`, WindowControls.ios.kt)
/// move the controls' band out of the Compose controller's safe area and into the top bars' leading/trailing padding.
final class MacWindowControlsHost: UIViewController {
    private let compose: UIViewController

    init(compose: UIViewController) {
        self.compose = compose
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(compose)
        compose.view.frame = view.bounds
        compose.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(compose.view)
        compose.didMove(toParent: self)
        // Temporary diagnostics for the Mac report of bottom sheets floating ~5 cm above the window's bottom edge
        // (Console.app, process AndBible, filter MAC-GEOMETRY / MAC-KEYBOARD): Compose lifts its dialogs by the
        // keyboard overlap it computes from these notifications and UIScreen geometry.
        for name in [UIResponder.keyboardWillShowNotification, UIResponder.keyboardWillHideNotification,
                     UIResponder.keyboardWillChangeFrameNotification] {
            NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] note in
                let begin = (note.userInfo?[UIResponder.keyboardFrameBeginUserInfoKey] as? NSValue)?.cgRectValue ?? .zero
                let end = (note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue ?? .zero
                appLog("MAC-KEYBOARD \(note.name.rawValue) begin=\(NSCoder.string(for: begin)) " +
                       "end=\(NSCoder.string(for: end)) \(self?.geometry() ?? "")")
            }
        }
    }

    private var lastGeometry = ""

    /// Window, screen and safe-area geometry as Compose sees it (points).
    private func geometry() -> String {
        guard let window = view.window else { return "window=nil" }
        let screen = window.screen
        let frameInScreen = window.coordinateSpace.convert(window.bounds, to: screen.coordinateSpace)
        let s = view.safeAreaInsets
        return "window=\(NSCoder.string(for: window.bounds)) screen=\(NSCoder.string(for: screen.bounds)) " +
            "scale=\(screen.scale) windowInScreen=\(NSCoder.string(for: frameInScreen)) " +
            "safe=(t=\(s.top) l=\(s.left) b=\(s.bottom) r=\(s.right))"
    }

    private func logGeometry() {
        let line = geometry()
        if line != lastGeometry {
            lastGeometry = line
            appLog("MAC-GEOMETRY \(line)")
        }
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        updateWindowControls()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateWindowControls()
        logGeometry()
    }

    private func updateWindowControls() {
        guard #available(iOS 26.0, *) else { return }
        let safe = view.safeAreaInsets
        let adapted = view.edgeInsets(for: .safeArea(cornerAdaptation: .horizontal))
        IosWindowControls.shared.update(
            controller: compose,
            safeTop: safe.top, safeLeft: safe.left, safeRight: safe.right,
            adaptedTop: adapted.top, adaptedLeft: adapted.left, adaptedRight: adapted.right
        )
    }
}
