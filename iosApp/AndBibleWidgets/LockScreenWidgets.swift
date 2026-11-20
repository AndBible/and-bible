import SwiftUI
import WidgetKit

/// Lock Screen accessories of the speak state: circular play/pause with a progress ring, rectangular reference + bar,
/// inline "Psalm 23:1 · 34 %". (The rectangular verse accessory is a family of the Verse widget.) The system draws
/// accessories monochrome, so nothing here uses colour.
struct SpeakLockScreenWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "AndBibleSpeakLock", provider: SpeakProvider()) { entry in
            SpeakLockView(entry: entry)
        }
        .configurationDisplayName("Speak")
        .description("Reading progress and play/pause on the Lock Screen.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct SpeakLockView: View {
    let entry: SpeakEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if let snap = entry.load.snapshot, snap.hasBibles {
            let s = snap.speak
            switch family {
            case .accessoryCircular:
                circular(s)
            case .accessoryInline:
                Text(inlineText(snap, s)).widgetLink(WidgetLinks.speak("toggle"))
            default:
                rectangular(snap, s)
            }
        } else {
            Text(WidgetSnapshot.openAppText(entry.load.snapshot)).widgetURL(WidgetLinks.openApp)
        }
    }

    @ViewBuilder private func circular(_ s: SpeakSnapshot) -> some View {
        let face = ZStack {
            AccessoryWidgetBackground()
            Gauge(value: Double(s.percent ?? 0), in: 0...100) {
                EmptyView()
            } currentValueLabel: {
                Image(systemName: s.isSpeaking ? "pause.fill" : "play.fill").font(.system(size: 18, weight: .semibold))
            }
            .gaugeStyle(.accessoryCircularCapacity)
        }
        if #available(iOS 17.0, *) {
            WidgetControl(kind: .speak(WidgetLinks.speak("toggle"))) { face }
        } else {
            face.widgetURL(WidgetLinks.speak("toggle"))
        }
    }

    /// Rectangular: the toggle is an intent on iOS 17 (acts without opening the app), a link on iOS 16.
    @ViewBuilder private func rectangular(_ snap: WidgetSnapshot, _ s: SpeakSnapshot) -> some View {
        let face = VStack(alignment: .leading, spacing: 2) {
            Text(s.title ?? s.reference ?? snap.text("widget_continue_listening", "Continue listening"))
                .font(.system(size: 12, weight: .bold)).lineLimit(1)
            Text(s.statusLine).font(.system(size: 11)).opacity(0.75).lineLimit(1)
            Gauge(value: Double(s.percent ?? 0), in: 0...100) { EmptyView() }.gaugeStyle(.accessoryLinearCapacity)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        if #available(iOS 17.0, *) {
            WidgetControl(kind: .speak(WidgetLinks.speak("toggle"))) { face }
        } else {
            face.widgetLink(WidgetLinks.speak("toggle"))
        }
    }

    /// The snapshot's own status line ("Psalm 23:1 · 34 % · WEB", already in the app language): the percent format is
    /// the app's, not hard-coded here.
    private func inlineText(_ snap: WidgetSnapshot, _ s: SpeakSnapshot) -> String {
        s.reference == nil ? snap.text("app_name", "AndBible") : s.statusLine
    }
}

private extension View {
    /// A lock screen accessory cannot hold a button on iOS 16; a tap anywhere runs the action by link.
    func widgetLink(_ url: URL) -> some View { widgetURL(url) }
}
