import SwiftUI
import WidgetKit

@main
struct AndBibleWidgetsBundle: WidgetBundle {
    @WidgetBundleBuilder var body: some Widget {
        SpeakWidget()
        ListeningListWidget()
        verseWidget
        SpeakLockScreenWidget()
    }

    /// The configurable Verse widget needs App Intents (iOS 17); iOS 16 gets the fixed one under the same kind.
    @WidgetBundleBuilder private var verseWidget: some Widget {
        if #available(iOS 17.0, *) { VerseWidget() } else { VerseWidgetLegacy() }
    }
}
