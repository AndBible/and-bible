import Foundation
import SharedUi
import WidgetKit

/// The widget kinds that show the speak state; a speak update reloads only these (the Verse widgets read the pools,
/// which do not change while speaking).
enum WidgetReloader {
    static let speakKinds = ["AndBibleSpeak", "AndBibleSpeakLock", "AndBibleListeningList"]

    static func reload(all: Bool) {
        if all {
            WidgetCenter.shared.reloadAllTimelines()
        } else {
            speakKinds.forEach { WidgetCenter.shared.reloadTimelines(ofKind: $0) }
        }
    }

    static func install() {
        IosPlatformBootstrap.shared.setWidgetReloader { all in reload(all: all.boolValue) }
    }
}

/// Runs a widget speak intent in the app process: starts the Kotlin side headless (Koin, databases, window repository)
/// and performs the speak action. See `IosWidgetIntents` (Kotlin).
enum WidgetIntentBridgeImpl {
    static func run(url: String) async -> Bool {
        // The headless launch may not have run the app's init: make sure the writer can reload the timelines.
        WidgetReloader.install()
        do {
            let ok = try await IosWidgetIntents.shared.run(url: url)
            return ok.boolValue
        } catch {
            appLog("widget intent failed: \(error)")
            return false
        }
    }
}
