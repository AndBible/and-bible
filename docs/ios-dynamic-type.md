# iOS Dynamic Type

## Pass bar

At XXXL (`UICTContentSizeCategoryXXXL`, scale 23/17) no text node is clipped or overlaps another and every control is reachable (scrolling allowed). At the accessibility sizes up to AX5 (`UICTContentSizeCategoryAccessibilityXXXL`, scale 53/17) every control stays reachable; fixed-height bars (top bars, toolbars, tab rows, bottom bars) cap their scale at `IOS_BAR_FONT_SCALE_CAP` through `CappedBarFontScale`, and a capped label that ellipsizes keeps its full text as the accessibility label. Bible text in the WebView follows only `font_size_multiplier` (as on Android); the system text size never scales it.

## Scale reference

Apple body point size divided by 17:

| Size | Raw category | Body points | Scale |
| --- | --- | --- | --- |
| XS | `UICTContentSizeCategoryXS` | 14 | 14/17 |
| S | `UICTContentSizeCategoryS` | 15 | 15/17 |
| M | `UICTContentSizeCategoryM` | 16 | 16/17 |
| L (default) | `UICTContentSizeCategoryL` | 17 | 1 |
| XL | `UICTContentSizeCategoryXL` | 19 | 19/17 |
| XXL | `UICTContentSizeCategoryXXL` | 21 | 21/17 |
| XXXL | `UICTContentSizeCategoryXXXL` | 23 | 23/17 |
| AX1 | `UICTContentSizeCategoryAccessibilityM` | 28 | 28/17 |
| AX2 | `UICTContentSizeCategoryAccessibilityL` | 33 | 33/17 |
| AX3 | `UICTContentSizeCategoryAccessibilityXL` | 40 | 40/17 |
| AX4 | `UICTContentSizeCategoryAccessibilityXXL` | 47 | 47/17 |
| AX5 | `UICTContentSizeCategoryAccessibilityXXXL` | 53 | 53/17 |

## Runtime measurement before implementation

The debug-only `-debugFontScaleProbe` launch argument exposes the effective
`LocalDensity.current.fontScale` as the accessibility value of `ab-font-scale`.
`DynamicTypeUITests.testMeasureFontScaleAtXXXL` retains the numeric measurement and
accessibility tree. Measure before deciding whether the app needs to supply an
explicit mapping: applying another multiplier when Compose already handles the
system category would scale text twice. The fixed-height bar cap applies either way.

On iPhone 16 / iOS 18.6 with Compose Multiplatform 1.11.1, the XXXL launch
measurement passed and reported `fontScale=1.3` (accessibility label and value).
Compose therefore already maps the system category. Its observed value differs
from Apple's reference body ratio `23/17`; the table above is a reference, not an
assertion that Compose uses those exact ratios. Do not apply the reference mapping
as a multiplier. A live-change diagnostic then showed UIKit app/window/root traits
at AX5 while the existing Compose density remained 1.0. The app therefore replaces
fontScale with the absolute reference-table value, preserving physical density,
and refreshes on content-size changes and returning to the foreground. It never
multiplies the built-in scale. The fixed-height bar cap still applies.

The launch measurement does not prove live content-size notification handling.
`DynamicTypeUITests.testFontScaleFollowsLiveSystemTextSizeChange` is an opt-in
integration check of the actual app's Compose density: the controller changes the
simulator's system category from default Large to AX5 while the app remains
running in the background, then foregrounds the same app. The
reference observer's injected-source unit test does not replace this check.
The fixed implementation's live check is pending. XXXL/AX5 Settings reachability
passed on iPhone and iPad; visual review still found ellipsized summaries at XXXL
and overlapping back/search controls at AX5. Those bar-adoption/layout fixes belong
to the feature-screen audit; the full visual pass bar is not yet satisfied.

## Audit evidence

Run the real app at XXXL and AX5. Reachability helpers may scroll to controls; use
stable row identifiers and list them in top-to-bottom order. Inspect screenshots
for clipping and overlap as well as inspecting the accessibility tree. A complete
accessibility label alone does not prove that the rendered glyphs fit, and a node
inside the window can still be clipped by an ancestor. In particular,
`assertNoTruncatedStaticText` is a bounded diagnostic check, not proof of the whole
pass bar. Verify capped, ellipsized labels retain their full accessible text.
