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

The debug-only `-debugFontScaleProbe` launch argument exposes the unmodified
`LocalDensity.current.fontScale` as the accessibility value of `ab-font-scale`.
`DynamicTypeUITests.testMeasureFontScaleAtXXXL` retains the numeric measurement and
accessibility tree. Measure before deciding whether the app needs to supply an
explicit mapping: applying another multiplier when Compose already handles the
system category would scale text twice. The fixed-height bar cap applies either way.

The measurement is pending; this document defines the acceptance criteria, not a
claim that the implementation or the screen audit has passed.

## Audit evidence

Run the real app at XXXL and AX5. Reachability helpers may scroll to controls; use
stable row identifiers and list them in top-to-bottom order. Inspect screenshots
for clipping and overlap as well as inspecting the accessibility tree. A complete
accessibility label alone does not prove that the rendered glyphs fit, and a node
inside the window can still be clipped by an ancestor. In particular,
`assertNoTruncatedStaticText` is a bounded diagnostic check, not proof of the whole
pass bar. Verify capped, ellipsized labels retain their full accessible text.
