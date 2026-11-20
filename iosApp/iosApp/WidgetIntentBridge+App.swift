import Foundation
import SharedUi
import WidgetKit

/// Runs a widget speak intent in the app process: starts the Kotlin side headless (Koin, databases, window repository)
/// and performs the speak action. See `IosWidgetIntents` (Kotlin).
enum WidgetIntentBridgeImpl {
    static func run(url: String) async -> Bool {
        // The headless launch may not have run the app's init: make sure the writer can reload the timelines.
        IosPlatformBootstrap.shared.setWidgetReloader { WidgetCenter.shared.reloadAllTimelines() }
        do {
            let ok = try await IosWidgetIntents.shared.run(url: url)
            return ok.boolValue
        } catch {
            appLog("widget intent failed: \(error)")
            return false
        }
    }
}
