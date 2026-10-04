"""Draws the link-preview images: the 1200x630 Open Graph card and the 180x180 apple-touch icon.

The card is what Facebook, WhatsApp, Signal, Telegram, X, Mastodon, Slack and Discord show when the
site is shared. It reuses the landing page's own material: the hero eyebrow and headline from
content/en/site.yaml, the site fonts and palette, the logo and the first two phone screens from
assets/img/appshots. Re-run after any of those changes:
    cd website && uv run python -m sitegen.ogimage
"""

from __future__ import annotations

import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from sitegen import paths
from sitegen.i18n import strings as load_strings

IMG = paths.ASSETS / "img"
FONTS = paths.ASSETS / "fonts"
OG = IMG / "og-default.jpg"
TOUCH_ICON = IMG / "apple-touch-icon.png"
LOGO = IMG / "logo-512.png"  # logo.svg rasterised (PIL cannot draw SVG)

WIDTH, HEIGHT = 1200, 630
SCALE = 2  # drawn at 2x, then downsampled, for smooth curves and text

# The light theme of assets/css/site.css.
GROUND = (247, 241, 227)
INK = (42, 38, 32)
MUTED = (93, 85, 70)
ACCENT = (217, 154, 43)
ACCENT_INK = (154, 91, 16)
BEZEL = (22, 23, 26)
GLOW = (254, 186, 42)


def _font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size * SCALE)


def _glow(canvas: Image.Image, centre: tuple[int, int], radius: int, alpha: int) -> None:
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    x, y = (c * SCALE for c in centre)
    r = radius * SCALE
    ImageDraw.Draw(layer).ellipse((x - r, y - r, x + r, y + r), fill=(*GLOW, alpha))
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(r // 2)))


def _phone(screen: Path, width: int, angle: float) -> Image.Image:
    """A phone in the page's own bezel style, rotated by `angle` degrees, with a soft shadow."""
    s = SCALE
    border, radius = 10 * s, 36 * s
    with Image.open(screen) as shot:
        inner_w = width * s - 2 * border
        shot = shot.convert("RGB").resize((inner_w, round(shot.height * inner_w / shot.width)), Image.LANCZOS)
    height = shot.height + 2 * border
    phone = Image.new("RGBA", (width * s, height), (0, 0, 0, 0))
    ImageDraw.Draw(phone).rounded_rectangle((0, 0, width * s - 1, height - 1), radius, fill=BEZEL)
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width - 1, shot.height - 1), radius - border, fill=255)
    phone.paste(shot, (border, border), mask)

    pad = 40 * s
    framed = Image.new("RGBA", (phone.width + 2 * pad, phone.height + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("RGBA", framed.size, (0, 0, 0, 0))
    shadow.paste((107, 79, 26, 90), (pad, pad + 14 * s), phone.getchannel("A"))
    framed.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(18 * s)))
    framed.alpha_composite(phone, (pad, pad))
    return framed.rotate(angle, Image.BICUBIC, expand=True)


def _headline(draw: ImageDraw.ImageDraw, text: str, x: int, y: int, size: int, max_width: int) -> int:
    """Draws `text` word-wrapped; *starred* words in the italic accent, as on the page. Returns the bottom y."""
    regular, italic = _font("source-serif-4-latin-600-normal.woff2", size), _font("source-serif-4-latin-600-italic.woff2", size)
    words = [(w.strip("*"), w.startswith("*")) for w in re.findall(r"\*[^*]+\*|\S+", text)]
    space = draw.textlength(" ", font=regular)
    line_height = round(size * 1.12) * SCALE
    cx, cy = x * SCALE, y * SCALE
    for word, emphasis in words:
        font = italic if emphasis else regular
        w = draw.textlength(word, font=font)
        if cx > x * SCALE and cx + w > (x + max_width) * SCALE:
            cx, cy = x * SCALE, cy + line_height
        draw.text((cx, cy), word, font=font, fill=ACCENT_INK if emphasis else INK)
        cx += w + space
    return cy // SCALE + round(size * 1.12)


def build_og(out: Path = OG) -> Path:
    site = load_strings(paths.CONTENT, paths.DEFAULT_LANG)
    s = SCALE
    canvas = Image.new("RGBA", (WIDTH * s, HEIGHT * s), (*GROUND, 255))
    _glow(canvas, (960, 140), 420, 70)
    _glow(canvas, (120, 620), 300, 30)

    shots = sorted((IMG / "appshots").glob("*.webp"))
    back = _phone(shots[1], 250, -7)
    front = _phone(shots[0], 270, 4)
    canvas.alpha_composite(back, (1000 * s - back.width // 2, 120 * s))
    canvas.alpha_composite(front, (800 * s - front.width // 2, 70 * s))

    draw = ImageDraw.Draw(canvas)
    left, text_width = 72, 560
    with Image.open(LOGO) as logo:
        canvas.alpha_composite(logo.convert("RGBA").resize((64 * s, 64 * s), Image.LANCZOS), (left * s, 72 * s))
    draw.text(((left + 80) * s, 104 * s), site["site_name"], font=_font("source-serif-4-latin-600-normal.woff2", 40),
              fill=INK, anchor="lm")

    eyebrow = _font("inter-latin-600-normal.woff2", 20)
    draw.text((left * s, 196 * s), site["hero"]["eyebrow"].upper(), font=eyebrow, fill=ACCENT_INK)
    bottom = _headline(draw, site["hero"]["headline"], left, 236, 66, text_width)

    draw.rounded_rectangle((left * s, (bottom + 28) * s, (left + 56) * s, (bottom + 34) * s), 3 * s, fill=ACCENT)
    draw.text((left * s, (bottom + 58) * s), "Android · iPhone · iPad  —  andbible.org",
              font=_font("inter-latin-400-normal.woff2", 24), fill=MUTED)

    image = canvas.convert("RGB").resize((WIDTH, HEIGHT), Image.LANCZOS)
    image.save(out, "JPEG", quality=86, optimize=True, progressive=True)
    return out


def build_touch_icon(out: Path = TOUCH_ICON) -> Path:
    """iOS draws its own rounded corners on the home screen, so the icon is an opaque square."""
    size, logo_size = 180, 136
    icon = Image.new("RGBA", (size, size), (*GROUND, 255))
    with Image.open(LOGO) as logo:
        icon.alpha_composite(logo.convert("RGBA").resize((logo_size, logo_size), Image.LANCZOS),
                             ((size - logo_size) // 2, (size - logo_size) // 2))
    icon.convert("RGB").save(out, "PNG", optimize=True)
    return out


if __name__ == "__main__":
    for path in (build_og(), build_touch_icon()):
        print(path.relative_to(paths.REPO), path.stat().st_size, "bytes")
