import SwiftUI
import WidgetKit

/// iOS 17+: the Verse widget is configurable through App Intents ("Edit Widget").
@available(iOS 17.0, *)
struct AndBibleWidgetsBundle17: WidgetBundle {
    var body: some Widget {
        SpeakWidget()
        ListeningListWidget()
        VerseWidget()
        SpeakLockScreenWidget()
    }
}

/// iOS 16: the same widgets, with the fixed Verse widget (same kind, no configuration).
struct AndBibleWidgetsBundle16: WidgetBundle {
    var body: some Widget {
        SpeakWidget()
        ListeningListWidget()
        VerseWidgetLegacy()
        SpeakLockScreenWidget()
    }
}

/// `WidgetBundleBuilder` has no `if #available`, so the entry point picks the bundle.
@main
struct AndBibleWidgetsEntry {
    static func main() {
        if #available(iOS 17.0, *) { AndBibleWidgetsBundle17.main() } else { AndBibleWidgetsBundle16.main() }
    }
}
