import SwiftUI
import WidgetKit
import AppIntents

/// A tappable widget control. iOS 17+: an App Intent button that acts without opening the app (speak intents run in
/// the app process in the background). iOS 16 and the `link` fallback: a `Link` to the `andbible-widget://` URL, which
/// opens the app and performs the action there.
struct WidgetControl<Label: View>: View {
    enum Kind {
        /// Speak intent for a widget URL (`speak/...`, `bookmark/...`, `speak-passage?...`).
        case speak(URL)
        case shuffle(key: String)
        /// Only a link, on every iOS version.
        case link(URL)
    }

    let kind: Kind
    @ViewBuilder let label: () -> Label

    var body: some View {
        switch kind {
        case let .speak(url):
            if #available(iOS 17.0, *) {
                Button(intent: SpeakWidgetIntent(url: url.absoluteString)) { label() }.buttonStyle(.plain)
            } else {
                Link(destination: url) { label() }
            }
        case let .shuffle(key):
            if #available(iOS 17.0, *) {
                Button(intent: VerseShuffleIntent(key: key)) { label() }.buttonStyle(.plain)
            } else {
                label().opacity(0.35)
            }
        case let .link(url):
            Link(destination: url) { label() }
        }
    }
}

/// "Open AndBible" card: shown when there is nothing to act on (no snapshot, no Bible installed, unknown version).
struct OpenAppView: View {
    let text: String
    let palette: WPalette
    var symbol = "book.fill"

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: symbol).font(.system(size: 22, weight: .semibold)).foregroundStyle(palette.primary).widgetAccentable()
            Text(text).font(.system(size: 13, weight: .semibold)).multilineTextAlignment(.center)
                .foregroundStyle(palette.onSurface)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .widgetURL(WidgetLinks.openApp)
    }
}

/// The small header: icon, text, optional trailing view.
struct WHeader<Trailing: View>: View {
    let symbol: String
    let text: String
    let palette: WPalette
    @ViewBuilder let trailing: () -> Trailing

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: symbol).font(.system(size: 12, weight: .semibold)).foregroundStyle(palette.primary).widgetAccentable()
            Text(text).font(.system(size: 12, weight: .semibold)).lineLimit(1).foregroundStyle(palette.onSurface2)
            Spacer(minLength: 0)
            trailing()
        }
    }
}

extension WHeader where Trailing == EmptyView {
    init(symbol: String, text: String, palette: WPalette) {
        self.init(symbol: symbol, text: text, palette: palette) { EmptyView() }
    }
}

/// Sleep timer chip: moon + remaining minutes.
struct SleepChip: View {
    let minutes: Int
    let text: String
    let palette: WPalette

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: "moon.fill").font(.system(size: 10, weight: .semibold))
            Text(text).font(.system(size: 11, weight: .semibold))
        }
        .foregroundStyle(palette.onSurface)
        .padding(.horizontal, 7).padding(.vertical, 3)
        .background(Capsule().fill(palette.track))
    }
}
