package net.bible.sharedcore.theme

import net.bible.service.common.DisplayColorMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorMathTest {
    @Test fun grayscale_collapses_rgb_to_equal_channels() {
        val g = toGrayscaleArgb(0xFFCC3300.toInt())
        val r = (g shr 16) and 0xFF; val gr = (g shr 8) and 0xFF; val b = g and 0xFF
        assertEquals(r, gr); assertEquals(gr, b)
        assertEquals(0xFF, (g ushr 24) and 0xFF, "alpha preserved")
        // A red "error"-role color must gray to equal channels too: this is the color math behind
        // AbTheme.grayscale() graying the ENTIRE M3 scheme (error included) in BW/COLOR_EINK.
        val err = toGrayscaleArgb(0xFFBA1A1A.toInt())
        val er = (err shr 16) and 0xFF; val eg = (err shr 8) and 0xFF; val eb = err and 0xFF
        assertTrue(er == eg && eg == eb, "error role must be grayscale in monochrome")
    }
    @Test fun accent_stays_colored_in_normal_and_color_eink_but_grays_in_bw() {
        val base = 0xFF2196F3.toInt()
        assertEquals(base, accentArgbFor(base, DisplayColorMode.NORMAL))
        assertEquals(base, accentArgbFor(base, DisplayColorMode.COLOR_EINK))
        val bw = accentArgbFor(base, DisplayColorMode.BW)
        val r = (bw shr 16) and 0xFF; val g = (bw shr 8) and 0xFF; val b = bw and 0xFF
        assertTrue(r == g && g == b, "BW accent must be gray")
    }

    // ---- Batch 4c: classic colour-picker maths ----

    @Test fun shade_blends_towards_white_for_positive_and_black_for_negative() {
        // Classic: t = (percent < 0) ? 0 : 255; p = |percent|; ch' = round((t - ch) * p) + ch.
        // 0x336699 at +0.5 -> r: round((255-51)*0.5)+51 = 153, g: round((255-102)*0.5)+102 = 179 (178.5
        // rounds HALF UP, Java Math.round semantics), b: round((255-153)*0.5)+153 = 204.
        assertEquals(0xFF99B3CC.toInt(), shadeArgb(0xFF336699.toInt(), 0.5))
        // -0.5 -> ch' = round((0-ch)*0.5)+ch = ceil(ch/2) for the half cases: r 26, g 51, b 77
        // (floor(-25.5 + 0.5) = -25, so r = 26; with kotlin.math.round g would come out 50, not 51).
        assertEquals(0xFF1A334D.toInt(), shadeArgb(0xFF336699.toInt(), -0.5))
        assertEquals(0xFF.toInt(), (shadeArgb(0xFF336699.toInt(), 0.9) ushr 24) and 0xFF)
    }

    @Test fun shade_saturates_at_the_extremes() {
        assertEquals(0xFFFFFFFF.toInt(), shadeArgb(0xFFFFFFFF.toInt(), 0.9), "white cannot get lighter")
        assertEquals(0xFF000000.toInt(), shadeArgb(0xFF000000.toInt(), -0.775), "black cannot get darker")
    }

    @Test fun color_shades_are_twelve_light_to_dark_and_exclude_the_input() {
        val base = 0xFF2196F3.toInt()
        val shades = colorShades(base)
        assertEquals(12, shades.size)
        assertTrue(shades.none { it == base }, "classic's percentages never reproduce the input")
        val lum = shades.map { relativeLuminance(it) }
        assertEquals(lum.sortedDescending(), lum, "ordered lightest -> darkest")
    }

    @Test fun presets_append_black_when_the_current_colour_is_already_material() {
        val list = presetColors(0xFF2196F3.toInt()) // BLUE 500 is in MATERIAL_PRESETS
        assertEquals(20, list.size)
        assertEquals(MATERIAL_PRESETS, list.dropLast(1), "the 19 keep classic's order")
        assertEquals(BLACK_ARGB, list.last(), "black fills 19 -> 20")
    }

    @Test fun presets_unshift_a_non_material_colour_and_then_skip_black() {
        val custom = 0xFF123456.toInt()
        val list = presetColors(custom)
        assertEquals(20, list.size)
        assertEquals(custom, list.first())
        assertTrue(BLACK_ARGB !in list, "already 20 long, so classic does not push black")
        assertEquals(1, list.count { it == custom }, "never duplicated")
    }

    @Test fun presets_carry_both_the_working_and_the_original_colour() {
        val working = 0xFF123456.toInt()
        val original = 0xFF654321.toInt()
        val list = presetColors(working, original)
        assertEquals(21, list.size)
        assertEquals(original, list[0], "the original is unshifted last, so it ends up first")
        assertEquals(working, list[1])
    }

    @Test fun light_colour_test_matches_classics_065_luminance_threshold() {
        // Classic tints the check mark black on light swatches (ColorUtils.calculateLuminance >= 0.65).
        assertTrue(isLightColor(0xFFFFEB3B.toInt()), "Material yellow is light")
        assertTrue(isLightColor(0xFFFFFFFF.toInt()))
        assertTrue(!isLightColor(0xFF3F51B5.toInt()), "Material indigo is dark")
        assertTrue(!isLightColor(0xFF000000.toInt()))
    }

    @Test fun hex_round_trips_as_six_uppercase_digits_without_alpha() {
        assertEquals("2196F3", hexOf(0xFF2196F3.toInt()))
        assertEquals("000000", hexOf(0xFF000000.toInt()))
        assertEquals("0A0B0C", hexOf(0xFF0A0B0C.toInt()), "zero-padded to six")
        assertEquals(0xFF2196F3.toInt(), parseHexColor(hexOf(0xFF2196F3.toInt())))
    }

    @Test fun hex_parses_three_and_six_digits_with_optional_hash_and_forces_alpha() {
        assertEquals(0xFF2196F3.toInt(), parseHexColor("2196f3"), "lower case accepted")
        assertEquals(0xFF2196F3.toInt(), parseHexColor("#2196F3"))
        assertEquals(0xFFAABBCC.toInt(), parseHexColor("abc"), "3 digits expand by doubling")
        assertEquals(0xFF123456.toInt(), parseHexColor("  123456 "), "surrounding space tolerated")
    }

    @Test fun hex_rejects_everything_classic_guessed_at() {
        // Classic invents a colour for 1/2/4/5/7-digit input (e.g. 4 digits -> g = r, r = 0).
        // Those rules were dropped deliberately: an unparseable field simply does not move the colour.
        listOf("", "1", "12", "1234", "12345", "1234567", "12345678", "12345g", "#", "xyz")
            .forEach { assertNull(parseHexColor(it), "must reject \"$it\"") }
    }

    @Test fun hsv_round_trips_within_one_step_for_the_primaries_and_greys() {
        listOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(), 0xFFFF00FF.toInt(),
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt(), 0xFF2196F3.toInt(),
        ).forEach { argb ->
            val (h, s, v) = argbToHsv(argb)
            val back = hsvToArgb(h, s, v)
            listOf(16, 8, 0).forEach { shift ->
                val a = (argb shr shift) and 0xFF
                val b = (back shr shift) and 0xFF
                assertTrue(kotlin.math.abs(a - b) <= 1, "channel $shift of $argb round-tripped to $back")
            }
            assertEquals(0xFF, (back ushr 24) and 0xFF, "always opaque")
        }
    }

    @Test fun hsv_wraps_hue_and_treats_grey_as_zero_saturation() {
        assertEquals(hsvToArgb(0f, 1f, 1f), hsvToArgb(360f, 1f, 1f))
        assertEquals(hsvToArgb(0f, 1f, 1f), hsvToArgb(-360f, 1f, 1f))
        assertEquals(0f, argbToHsv(0xFF808080.toInt()).second, "grey has no saturation")
    }

    @Test fun sat_val_maps_the_square_corners_and_clamps_outside_it() {
        assertEquals(0f to 1f, satValFromOffset(0f, 0f, 200f, 200f), "top-left: no saturation, full value")
        assertEquals(1f to 1f, satValFromOffset(200f, 0f, 200f, 200f), "top-right")
        assertEquals(0f to 0f, satValFromOffset(0f, 200f, 200f, 200f), "bottom-left")
        assertEquals(1f to 0f, satValFromOffset(200f, 200f, 200f, 200f), "bottom-right")
        assertEquals(0.5f to 0.5f, satValFromOffset(100f, 100f, 200f, 200f))
        assertEquals(0f to 1f, satValFromOffset(-50f, -50f, 200f, 200f), "clamped, not wrapped")
        assertEquals(1f to 0f, satValFromOffset(500f, 500f, 200f, 200f))
    }

    @Test fun hue_runs_360_at_the_top_to_0_at_the_bottom_and_clamps() {
        // Tolerances where the fraction is not exact in binary floating point (100/300).
        assertEquals(360f, hueFromOffset(0f, 300f), 0.01f)
        assertEquals(0f, hueFromOffset(300f, 300f), 0.01f)
        assertEquals(180f, hueFromOffset(150f, 300f), 0.01f)
        assertEquals(360f, hueFromOffset(-20f, 300f), 0.01f, "clamped at the top")
        assertEquals(0f, hueFromOffset(9999f, 300f), 0.01f, "clamped at the bottom")
    }

    @Test fun the_offset_mappings_are_inverses() {
        // 0.75/0.25 and 240/300 are chosen because they are exact in binary; hueFromOffset is
        // asserted with a tolerance because 100/300 is not.
        assertEquals(150f to 150f, satValToOffset(0.75f, 0.25f, 200f, 200f))
        assertEquals(0.75f to 0.25f, satValFromOffset(150f, 150f, 200f, 200f))
        assertEquals(100f, hueToOffset(240f, 300f), 0.01f)
        assertEquals(240f, hueFromOffset(100f, 300f), 0.01f)
    }

    @Test fun the_offset_mappings_survive_a_zero_sized_panel() {
        // A composable is measured before it is drawn; a gesture arriving at size 0 must not divide by it.
        assertEquals(0f to 0f, satValFromOffset(10f, 10f, 0f, 0f))
        assertEquals(0f, hueFromOffset(10f, 0f))
    }

    // ---- "Choose passage" grid category tint ----

    @Test fun blend_returns_the_endpoints_and_the_midpoint() {
        val base = 0xFF204060.toInt()
        val tint = 0xFFA0C0E0.toInt()
        assertEquals(base, blendArgb(base, tint, 0f), "fraction 0 is the base")
        assertEquals(tint, blendArgb(base, tint, 1f), "fraction 1 is the tint")
        // Each channel is 0x80 apart, so the midpoint adds 0x40 to each.
        assertEquals(0xFF6080A0.toInt(), blendArgb(base, tint, 0.5f))
    }

    @Test fun blend_clamps_the_fraction_and_forces_opaque_alpha() {
        val base = 0xFF204060.toInt()
        val tint = 0xFFA0C0E0.toInt()
        assertEquals(base, blendArgb(base, tint, -1f), "clamped below")
        assertEquals(tint, blendArgb(base, tint, 5f), "clamped above")
        // The chip is drawn as a Surface colour, never composited over anything, so a translucent
        // input must not leak a translucent fill.
        assertEquals(0xFF, (blendArgb(0x00204060, 0x40A0C0E0, 0.5f) ushr 24) and 0xFF)
    }

    @Test fun contrast_ratio_matches_the_wcag_endpoints() {
        val white = 0xFFFFFFFF.toInt()
        val black = BLACK_ARGB
        assertEquals(21.0, contrastRatio(white, black), 0.01)
        assertEquals(21.0, contrastRatio(black, white), 0.01, "symmetric")
        assertEquals(1.0, contrastRatio(white, white), 0.001, "a colour against itself")
        assertEquals(1.0, contrastRatio(black, black), 0.001)
    }

    @Test fun blend_keeps_every_channel_inside_the_base_to_tint_interval() {
        val base = 0xFF49454F.toInt()
        val tint = 0xFFFFFECD.toInt()
        for (f in listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 1f)) {
            val blend = blendArgb(base, tint, f)
            for (shift in listOf(16, 8, 0)) {
                val b = (base shr shift) and 0xFF
                val t = (tint shr shift) and 0xFF
                val v = (blend shr shift) and 0xFF
                assertTrue(v >= minOf(b, t) && v <= maxOf(b, t), "f=$f channel $shift: $v not in $b..$t")
                // +1 for the truncation in blendArgb's Float -> Int conversion.
                assertTrue(abs(v - b) <= f * abs(t - b) + 1, "f=$f channel $shift moved too far")
            }
        }
    }

    /**
     * What is deliberately NOT asserted about [blendArgb], because both are false — and both were
     * written as assertions in this test's first draft, failed, and were the reason the chip's whole
     * mechanism changed:
     *
     * - **A luminance shift bounded by the blend fraction.** The blend is per-channel in gamma-encoded
     *   sRGB while relative luminance decodes it through a convex transfer function, so at the bright
     *   end a quarter-step in channel space is more than a quarter-step in luminance.
     * - **That the blend's luminance lies between the endpoints'.** Channels move in opposite
     *   directions, so luminance is not monotone along the blend.
     *
     * Both are pinned here as characterisation tests: they are surprising enough that someone will
     * eventually "fix" one of them, and these say out loud that the behaviour is understood.
     */
    @Test fun blend_luminance_is_neither_fraction_bounded_nor_monotone() {
        // #FFFFFF a quarter of the way to Acts blue #0099FF: luminance 1.0 -> ~0.75, a 0.25 drop where
        // a linear budget over the 0.71 endpoint gap would allow only ~0.18.
        val bright = blendArgb(0xFFFFFFFF.toInt(), 0xFF0099FF.toInt(), 0.25f)
        assertEquals(0xFFBFE5FF.toInt(), bright)
        val drop = relativeLuminance(0xFFFFFFFF.toInt()) - relativeLuminance(bright)
        assertTrue(drop > 0.25 * abs(relativeLuminance(0xFF0099FF.toInt()) - 1.0), "shift exceeds a linear budget")

        // #E7E0EC a quarter of the way to the Wisdom green #99FF99: red 231->153 and blue 236->153 fall
        // while only green rises, so the result is DARKER than the base even though the tint is
        // lighter. The fraction is spelled out rather than taken from CATEGORY_TINT_FRACTION: this is a
        // fact about blendArgb at 0.25, and the chip's dial is free to move.
        val base = 0xFFE7E0EC.toInt()
        val tint = 0xFF99FF99.toInt()
        val blend = blendArgb(base, tint, 0.25f)
        assertTrue(relativeLuminance(tint) > relativeLuminance(base), "the tint is the lighter of the two")
        assertTrue(
            relativeLuminance(blend) < relativeLuminance(base),
            "yet the blend is darker than the base: #${hexOf(blend)}",
        )
    }

    @Test fun luminance_matching_hits_the_target_and_keeps_the_extremes_reachable() {
        val magenta = 0xFFFE33FF.toInt()
        for (target in listOf(0.0, 0.02, 0.12, 0.35, 0.76, 0.98, 1.0)) {
            val matched = luminanceMatchedArgb(magenta, target)
            assertEquals(target, relativeLuminance(matched), 0.02, "target $target -> #${hexOf(matched)}")
        }
        // The bracket really does span black to white, so no target is out of reach.
        assertEquals(0xFF000000.toInt(), luminanceMatchedArgb(magenta, 0.0))
        assertEquals(0xFFFFFFFF.toInt(), luminanceMatchedArgb(magenta, 1.0))
    }

    /**
     * The property the whole design rests on, and — unlike the fraction-bounded claim it replaced —
     * genuinely scheme-independent: the chip lands at essentially the surface's own luminance, so the
     * scheme's `surfaceVariant`/`onSurfaceVariant` contrast survives whatever seed A/B batch 4b's
     * MaterialKolor derivation produces. The absolute contrast figures against real stock and seeded
     * schemes are measured separately in `GridCategoryTintContrastTest`.
     */
    @Test fun the_category_chip_lands_at_the_surface_luminance() {
        // The real M3 surfaceVariants plus a dense grey sweep. The sweep matters: the worst drift is
        // NOT at the extremes but in the middle (mid-grey #949494 against the Revelation magenta), so a
        // list of only the stock values would miss it. `surfaceVariant` is a low-chroma neutral in every
        // seeded scheme, which is why greys are the right sweep.
        val bases = listOf(0xFF1D1B20.toInt(), 0xFF49454F.toInt(), 0xFFCAC4D0.toInt(), 0xFFE7E0EC.toInt()) +
            (0..255 step 8).map { 0xFF000000.toInt() or (it shl 16) or (it shl 8) or it }
        // 0..9 are the real categories; -1 and 99 both fall through to the OTHER base.
        val groups = (0..9).toList() + listOf(-1, 99)
        // 0.05 is a measured ceiling, not a design target: the worst drift over this sweep is 0.044.
        // Gamma-space blending of two colours of equal luminance but very different chroma does not
        // preserve luminance exactly, and at mid-grey that error peaks. Harmless — the contrast
        // consequence is measured directly in GridCategoryTintContrastTest (worst 5.45:1 against 5.44:1
        // untinted) — but it is why this asserts "lands at" and not "equals".
        for (base in bases) {
            for (group in groups) {
                val chip = categoryChipArgb(base, categoryBaseArgb(group))
                val drift = abs(relativeLuminance(chip) - relativeLuminance(base))
                assertTrue(
                    drift <= 0.05,
                    "base #${hexOf(base)}, group $group -> #${hexOf(chip)} drifted $drift in luminance",
                )
            }
        }
    }

    @Test fun the_category_chip_actually_carries_the_hue_it_is_given() {
        // The counterpart of the luminance test: matching lightness must not flatten the chip back to
        // the surface. On the stock light and dark surfaceVariants every category must be a distinct
        // colour, or the signal this exists to restore is not there.
        for (base in listOf(0xFFE7E0EC.toInt(), 0xFF49454F.toInt())) {
            val chips = (0..9).map { categoryChipArgb(base, categoryBaseArgb(it)) }
            chips.forEachIndexed { i, chip ->
                assertTrue(chip != base, "group $i left the chip at the plain surface #${hexOf(base)}")
            }
            // Major prophets (#FF99FF) and Revelation (#FE33FF) are two magentas classic separates only
            // by saturation, which the luminance match collapses on a light surface — a known and
            // accepted cost (see categoryChipArgb), so the count is 9 distinct rather than 10 there.
            val distinct = chips.distinct().size
            assertTrue(distinct >= 9, "only $distinct distinct chips on #${hexOf(base)}: $chips")
        }
    }

    @Test fun every_category_has_its_own_base_and_unknown_groups_fall_back() {
        val real = (0..9).map { categoryBaseArgb(it) }
        assertEquals(real.size, real.distinct().size, "the ten categories must be distinguishable")
        val other = categoryBaseArgb(-1)
        assertEquals(other, categoryBaseArgb(99), "any out-of-range group is OTHER")
        assertEquals(other, categoryBaseArgb(10))
        real.forEach { assertEquals(0xFF, (it ushr 24) and 0xFF, "category bases are opaque") }
    }
}
