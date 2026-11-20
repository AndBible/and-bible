import Foundation

/// The extension cannot link the Kotlin framework; the system never runs an `AudioPlaybackIntent` here.
enum WidgetIntentBridgeImpl {
    static func run(url: String) async -> Bool { false }
}
