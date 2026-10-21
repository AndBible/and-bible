import SwiftUI
import SharedUi

@main
struct iOSApp: App {
    init() {
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

/// Bridges the shared Compose UI (a Kotlin `UIViewController` exported by the SharedUi
/// framework) into SwiftUI. `MainViewControllerKt.MainViewController()` is the Kotlin
/// top-level `MainViewController()` in `sharedUi/iosMain`; it shows the AndBible I0 PoC app.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
