import Foundation
import SharedUi

/// Runs a widget speak intent in the app process: starts the Kotlin side headless (Koin, databases, window repository)
/// and performs the speak action. See `IosWidgetIntents` (Kotlin).
enum WidgetIntentBridgeImpl {
    static func run(url: String) async -> Bool {
        do {
            let ok = try await IosWidgetIntents.shared.run(url: url)
            return ok.boolValue
        } catch {
            appLog("widget intent failed: \(error)")
            return false
        }
    }
}
