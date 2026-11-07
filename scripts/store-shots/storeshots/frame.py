"""Framing: warm background, caption band at the top, device frame holding the screenshot."""
from __future__ import annotations

import os
import unicodedata
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import yaml
from PIL import Image, ImageDraw, ImageFilter, ImageFont, features

from .errors import ShotError
from .locales import locale_for
from .targets import Target
from .validate import validate_raw

CAPTION_COLOR = "#2b1600"
_REPO = Path(__file__).resolve().parents[3]


def style_dir() -> Path:
    return Path(os.environ.get("STORE_SHOTS_STYLE", _REPO / "store-screenshots" / "style"))


@dataclass(frozen=True)
class FontSpec:
    path: Path
    index: int = 0


@lru_cache(maxsize=None)
def _fonts_config(style: Path) -> dict:
    return yaml.safe_load((style / "fonts.yaml").read_text())


def _lang_key(lang: str, languages: dict) -> str:
    if lang in languages:
        return lang
    if lang.startswith("zh"):
        return "zh-rTW" if any(t in lang for t in ("TW", "HK", "Hant")) else "zh-rCN"
    return lang.split("-")[0].split("+")[0] if lang.split("-")[0] in languages else "default"


def font_for(lang: str) -> FontSpec:
    """Noto Bold face for the caption language (app code like zh-rCN, or BCP-47 like he)."""
    style = style_dir()
    cfg = _fonts_config(style)
    key = cfg["languages"][_lang_key(lang, cfg["languages"])]
    entry = cfg["fonts"][key]
    return FontSpec(style / "fonts" / entry["file"], entry.get("index", 0))


def is_rtl(lang: str) -> bool:
    primary = lang.split("-")[0]
    return lang in _fonts_config(style_dir())["rtl"] or primary in _fonts_config(style_dir())["rtl"]


def _lang_tag(lang: str) -> str:
    try:
        return locale_for(lang).bcp47
    except KeyError:
        return lang


class CaptionFont:
    """A Pillow font plus the text direction and language for raqm shaping."""

    def __init__(self, lang: str, size: int):
        if not features.check("raqm"):
            raise ShotError("caption", "Pillow has no libraqm: Arabic/Indic captions cannot be shaped")
        spec = font_for(lang)
        if not spec.path.exists():
            raise ShotError("caption", f"font {spec.path} missing: run store-screenshots/style/fetch-fonts.sh")
        self.font = ImageFont.truetype(str(spec.path), size, index=spec.index,
                                       layout_engine=ImageFont.Layout.RAQM)
        self.size = size
        self.direction = "rtl" if is_rtl(lang) else "ltr"
        self.language = _lang_tag(lang)

    def getlength(self, text: str) -> float:
        return self.font.getlength(text, direction=self.direction, language=self.language)


def load_font(lang: str, size: int) -> CaptionFont:
    return CaptionFont(lang, size)


def _wrap(text: str, font: CaptionFont, box_width: float) -> list[str] | None:
    """Greedy wrap into lines no wider than box_width, or None if a unit alone is too wide."""
    words = text.split(" ")
    spaced = len(words) > 1
    units = words if spaced else _clusters(text)
    sep = " " if spaced else ""
    lines, cur = [], ""
    for unit in units:
        trial = cur + (sep if cur else "") + unit
        if font.getlength(trial) <= box_width:
            cur = trial
            continue
        if not cur:
            return None
        lines.append(cur)
        if font.getlength(unit) > box_width:
            return None
        cur = unit
    lines.append(cur)
    return lines


def _clusters(text: str) -> list[str]:
    """Characters of an unspaced text (CJK, Thai) with combining marks kept on their base."""
    out: list[str] = []
    for ch in text:
        if out and unicodedata.category(ch) in ("Mn", "Mc", "Me"):
            out[-1] += ch
        else:
            out.append(ch)
    return out


def layout_caption(text: str, lang: str, box_width: float, max_font: int, min_font: int) -> tuple[int, list[str]]:
    """Largest font size in [min_font, max_font] at which text wraps to at most 2 lines."""
    text = " ".join(text.split())
    for size in range(max_font, min_font - 1, -2):
        lines = _wrap(text, load_font(lang, size), box_width)
        if lines is not None and len(lines) <= 2:
            return size, lines
    raise ShotError("caption", f"caption does not fit in 2 lines of {box_width:.0f}px at {min_font}px: {text!r}")


def draw_caption_lines(draw: ImageDraw.ImageDraw, lines: list[str], lang: str, size: int,
                       box_width: float, y: float, fill, x0: float = 0, line_height: float = 1.22) -> None:
    font = load_font(lang, size)
    for i, line in enumerate(lines):
        x = x0 + (box_width - font.getlength(line)) / 2
        draw.text((x, y + i * size * line_height), line, font=font.font, fill=fill,
                  direction=font.direction, language=font.language)


@lru_cache(maxsize=None)
def _frames_config(style: Path) -> dict:
    return yaml.safe_load((style / "frames.yaml").read_text())


def _cover(img: Image.Image, size: tuple[int, int]) -> Image.Image:
    scale = max(size[0] / img.width, size[1] / img.height)
    resized = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
    left, top = (resized.width - size[0]) // 2, (resized.height - size[1]) // 2
    return resized.crop((left, top, left + size[0], top + size[1]))


def frame_shot(raw: Path, target: Target, caption: str, lang: str, out: Path) -> Path:
    """Compose background + caption + framed screenshot at exactly target.size; save RGB to out."""
    raw, out = Path(raw), Path(out)
    validate_raw(raw, target)
    style = style_dir()
    W, H = target.size
    short = min(W, H)

    # caption band: a fixed height for up to two lines, so the device sits at the same place
    max_font = round(short * 0.062)
    min_font = round(max_font * 0.62)
    margin = round(W * 0.05)
    box = W - 2 * margin
    size, lines = layout_caption(caption, lang, box, max_font, min_font)
    pad = round(max_font * 0.55)
    band = round(pad + 2 * max_font * 1.22 + pad * 0.3)

    canvas = _cover(Image.open(style / "background.png").convert("RGB"), (W, H))
    draw = ImageDraw.Draw(canvas)
    block = size * 1.22 * (len(lines) - 1) + size
    y = pad + (2 * max_font * 1.22 - block) / 2
    draw_caption_lines(draw, lines, lang, size, box, y, CAPTION_COLOR, x0=margin)

    cfg = _frames_config(style)[target.frame]
    fw, fh = cfg["size"]
    avail = H - band - (0 if cfg["max_overflow"] else round(H * 0.035))
    scale = min(W * cfg["max_width"] / fw, avail * (1 + cfg["max_overflow"]) / fh)
    dw, dh = round(fw * scale), round(fh * scale)
    sx, sy, sw, sh = (round(v * scale) for v in cfg["screen"])
    with Image.open(raw) as r:
        shot = r.convert("RGB")
    fit = min(sw / shot.width, sh / shot.height)  # uniform scale, aspect preserved
    shot = shot.resize((round(shot.width * fit), round(shot.height * fit)), Image.LANCZOS)

    device = Image.new("RGBA", (dw, dh), (0, 0, 0, 0))
    mask = Image.new("L", (sw * 2, sh * 2), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw * 2 - 1, sh * 2 - 1),
                                           radius=round(cfg["radius"] * scale * 2), fill=255)
    mask = mask.resize((sw, sh), Image.LANCZOS)
    device.paste(shot, (sx + (sw - shot.width) // 2, sy + (sh - shot.height) // 2))
    layer_mask = Image.new("L", (dw, dh), 0)
    layer_mask.paste(mask, (sx, sy))
    device.putalpha(layer_mask)
    bezel = Image.open(style / "frames" / f"{target.frame}.png").resize((dw, dh), Image.LANCZOS)
    device = Image.alpha_composite(device, bezel)

    x = (W - dw) // 2
    top = band
    shadow = Image.new("L", canvas.size, 0)
    shadow.paste(device.getchannel("A").point(lambda a: a * 0.35), (x, top + round(dh * 0.012)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(max(4, W * 0.012)))
    canvas.paste(Image.new("RGB", canvas.size, (70, 30, 0)), (0, 0), shadow)
    canvas.paste(device, (x, top), device)

    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_name(out.name + ".tmp" + out.suffix)
    if out.suffix.lower() == ".webp":
        canvas.save(tmp, quality=90, method=6)
    else:
        canvas.save(tmp, optimize=True)
    tmp.replace(out)
    return out
