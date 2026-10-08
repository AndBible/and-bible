import SwiftUI
import SharedUi

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
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
