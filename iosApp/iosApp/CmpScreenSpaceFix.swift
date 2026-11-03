import UIKit
import ObjectiveC
import SharedUi

/// The iOS app on a Mac: Compose's sheets and dialogs float above the window's bottom edge when the window is taller
/// than `UIScreen.bounds` (macOS 26, "Designed for iPad": a 1284 pt window on a 1050 pt screen).
///
/// Compose Multiplatform 1.11.1 (`ComposeSceneKeyboardOffsetManager.start()`, run for every scene layer when it
/// appears, i.e. every material3 sheet and dialog) computes the keyboard overlap as
/// `max(0, keyboardHeight - viewBottomIndent)`, where `viewBottomIndent = screen.bounds.height - (the layer view's
/// bottom converted into screen.coordinateSpace)`. On a Mac the window's points are not the screen's: the view's bottom
/// lies 234 pt below the screen's, the indent is -234 and the "overlap" is 234 pt with no keyboard at all. That overlap
/// is the layer's `WindowInsets.ime`, and material3 1.9.0's `ModalBottomSheet` is a CMP `Dialog` with
/// `useSoftwareKeyboardInset` on, so the sheet is laid out in the window minus 234 pt. Silencing CMP's keyboard listener
/// (`CmpKeyboardOverlap`, IosAppOnMacInsets.kt) does not reach this: it is computed on appearance, not on a notification.
///
/// The fix clamps the screen coordinate space's `convertPoint:fromCoordinateSpace:` for Compose's overlay views to
/// the screen's bottom edge, so the indent is never negative and the phantom overlap is 0. Active where
/// `IosAppOnMac.correctsInsets` is (the Mac, or debug builds launched with `-simulateMacInsets`); iPhone and iPad keep
/// UIKit's method untouched.
///
/// `-simulateTallMacWindow` (debug builds only) reproduces the Mac geometry on a simulator: the same conversion reports
/// Compose's views [simulatedOverhang] points lower, as if the window reached that far below the screen.
/// `ReadingUITests.testQuickSheetSitsOnWindowBottomInTallMacWindow` runs it with and without the fix.
enum CmpScreenSpaceFix {
    /// The Mac's measured overhang: window 1284 pt on a 1050 pt screen.
    static let simulatedOverhang: CGFloat = 234

    private static var installed = false

    /// Installs the clamp (and in debug builds the simulation) before Compose shows its first scene.
    static func installIfNeeded() {
        guard !installed else { return }
        installed = true
        let clamp = IosAppOnMac.shared.correctsInsets
        var overhang: CGFloat = 0
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-simulateTallMacWindow") { overhang = simulatedOverhang }
        #endif
        guard clamp || overhang > 0 else { return }

        let space: AnyObject = UIScreen.main.coordinateSpace
        let selector = NSSelectorFromString("convertPoint:fromCoordinateSpace:")
        guard let cls = object_getClass(space), let method = class_getInstanceMethod(cls, selector) else {
            appLog("MAC-SCREEN-SPACE not installed: no convertPoint:fromCoordinateSpace: on \(type(of: space))")
            return
        }
        typealias Convert = @convention(c) (AnyObject, Selector, CGPoint, AnyObject) -> CGPoint
        let original = unsafeBitCast(method_getImplementation(method), to: Convert.self)
        let replacement: @convention(block) (AnyObject, CGPoint, AnyObject) -> CGPoint = { me, point, from in
            let converted = original(me, selector, point, from)
            guard let view = from as? UIView, isComposeView(view) else { return converted }
            var result = converted
            result.y += overhang
            // The view never reaches below the screen, so CMP's indent is never negative.
            if clamp, let screenSpace = me as? UICoordinateSpace {
                result.y = min(result.y, screenSpace.bounds.maxY)
            }
            appLog("MAC-SCREEN-SPACE \(NSStringFromClass(object_getClass(view)!)) point=\(NSCoder.string(for: point)) " +
                   "converted y=\(converted.y) -> \(result.y) screen=\(NSCoder.string(for: (me as? UICoordinateSpace)?.bounds ?? .zero))")
            return result
        }
        class_replaceMethod(cls, selector, imp_implementationWithBlock(replacement), method_getTypeEncoding(method))
        appLog("MAC-SCREEN-SPACE installed on \(NSStringFromClass(cls)) clamp=\(clamp) simulatedOverhang=\(overhang)")
    }

    /// The scene's overlay view, the only view CMP converts into screen space. Kotlin/Native registers it under its
    /// Kotlin name with a module prefix and a counter (measured: `SharedUiandroidx.compose.ui.window.OverlayInputView9`).
    private static func isComposeView(_ view: UIView) -> Bool {
        object_getClass(view).map { NSStringFromClass($0).contains("androidx.compose.ui.window.OverlayInputView") } ?? false
    }
}
