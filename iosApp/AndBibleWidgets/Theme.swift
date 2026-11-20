import SwiftUI
import WidgetKit

/// Colours of the widgets. Full colour: the app's blue accent on system widget backgrounds (white / #1c1c1e). Accented
/// and vibrant (tinted, clear, Lock Screen): the system recolours everything, so nothing relies on hue; the play
/// button and the progress bar stay distinguishable by shape (outlined ring, filled bar) and contrast.
struct WPalette {
    let fullColor: Bool
    let onSurface: Color
    let onSurface2: Color
    let primary: Color
    let onPrimary: Color
    let track: Color
    let container: Color

    init(scheme: ColorScheme, mode: WidgetRenderingMode) {
        fullColor = mode == .fullColor
        let dark = scheme == .dark
        if mode == .fullColor {
            onSurface = dark ? Color(red: 0.88, green: 0.91, blue: 0.95) : Color(red: 0.08, green: 0.14, blue: 0.23)
            onSurface2 = dark ? Color(red: 0.64, green: 0.69, blue: 0.76) : Color(red: 0.27, green: 0.34, blue: 0.42)
            primary = dark ? Color(red: 0.65, green: 0.78, blue: 1.0) : Color(red: 0.18, green: 0.36, blue: 0.60)
            onPrimary = dark ? Color(red: 0.04, green: 0.18, blue: 0.36) : .white
            track = dark ? Color(red: 0.15, green: 0.19, blue: 0.25) : Color(red: 0.80, green: 0.85, blue: 0.93)
            container = dark ? Color(red: 0.11, green: 0.11, blue: 0.118) : .white
        } else {
            onSurface = .primary
            onSurface2 = .secondary
            primary = .primary
            onPrimary = .primary
            track = Color.primary.opacity(0.22)
            container = .clear
        }
    }
}

extension View {
    /// iOS 17+ `containerBackground`; plain background before.
    @ViewBuilder func widgetBackground(_ color: Color) -> some View {
        if #available(iOS 17.0, *) { containerBackground(color, for: .widget) } else { background(color) }
    }
}

extension View {
    /// iOS 17+ gives widget content its own margins; before, the content pads itself.
    @ViewBuilder func widgetContentPadding(_ amount: CGFloat = 14) -> some View {
        if #available(iOS 17.0, *) { self } else { padding(amount) }
    }
}

/// The 3 pt rounded progress bar: `fraction` 0...1, primary on the track.
struct WProgressBar: View {
    let fraction: Double
    let palette: WPalette
    var height: CGFloat = 4

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(palette.track)
                Capsule().fill(palette.primary)
                    .frame(width: max(0, min(1, fraction)) * geo.size.width)
                    .widgetAccentable()
            }
        }
        .frame(height: height)
    }
}

/// A round control glyph; `primary` is the filled play/pause (an outlined ring in accented modes).
struct WIcon: View {
    let symbol: String
    let palette: WPalette
    var size: CGFloat = 36
    var glyph: CGFloat = 18
    var primary = false

    var body: some View {
        ZStack {
            if primary {
                if palette.fullColor {
                    Circle().fill(palette.primary)
                } else {
                    Circle().strokeBorder(palette.primary, lineWidth: 2)
                }
            }
            Image(systemName: symbol)
                .font(.system(size: glyph, weight: .semibold))
                .foregroundStyle(primary && palette.fullColor ? palette.onPrimary : palette.onSurface)
        }
        .frame(width: size, height: size)
        .contentShape(Rectangle())
        .widgetAccentable(primary)
    }
}
