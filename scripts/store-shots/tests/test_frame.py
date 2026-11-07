import pytest
from fontTools.ttLib import TTFont, TTCollection
from PIL import Image, ImageDraw, ImageFont, features

from storeshots import frame
from storeshots.targets import TARGETS
from storeshots.validate import ShotError


def synthetic_raw(path, size):
    img = Image.new("RGB", size, (235, 235, 235))
    d = ImageDraw.Draw(img)
    for i in range(0, size[1], 80):
        d.line([(0, i), (size[0], i)], fill=(180, 180, 200), width=3)
    d.rectangle([0, 0, size[0] - 1, size[1] - 1], outline=(255, 0, 0), width=6)
    img.save(path)
    return path


@pytest.mark.parametrize("target_id", list(TARGETS))
def test_frame_output_size(tmp_path, target_id):
    target = TARGETS[target_id]
    raw = synthetic_raw(tmp_path / "raw.png", target.raw_size)
    out = frame.frame_shot(raw, target, "Highlight with your own labels", "en",
                           tmp_path / ("out" + (".webp" if target.store == "website" else ".png")))
    img = Image.open(out)
    assert img.size == target.size
    assert img.mode == "RGB"


def test_appstore_no_alpha(tmp_path):
    from storeshots.validate import validate_final
    target = TARGETS["ios-iphone-69"]
    raw = synthetic_raw(tmp_path / "raw.png", target.raw_size)
    out = frame.frame_shot(raw, target, "Notes", "en", tmp_path / "out.png")
    assert Image.open(out).mode == "RGB"
    validate_final(out, target)
    rgba = tmp_path / "rgba.png"
    Image.new("RGBA", target.size, (1, 2, 3, 255)).save(rgba)
    with pytest.raises(ShotError):
        validate_final(rgba, target)


def test_validate_final_rejects_wrong_size(tmp_path):
    from storeshots.validate import validate_final
    p = tmp_path / "x.png"
    Image.new("RGB", (100, 100)).save(p)
    with pytest.raises(ShotError, match="1320x2868"):
        validate_final(p, TARGETS["ios-iphone-69"])


def test_raw_wrong_size_rejected(tmp_path):
    from storeshots.validate import validate_raw
    target = TARGETS["play-phone"]
    raw = synthetic_raw(tmp_path / "strongs-parallel.png", (1080, 2300))
    with pytest.raises(ShotError) as e:
        validate_raw(raw, target)
    msg = str(e.value)
    assert "strongs-parallel" in msg and "1080x2300" in msg and "1440x3120" in msg
    with pytest.raises(ShotError):
        frame.frame_shot(raw, target, "x", "en", tmp_path / "o.png")
    assert not (tmp_path / "o.png").exists()


def test_long_caption_wraps_two_lines():
    text = "Hervorheben mit Ihren eigenen Etiketten und Notizen mit echter Formatierung für jeden Vers"
    assert len(text) >= 90
    box = 1080 - 2 * 48
    size, lines = frame.layout_caption(text, "de", box, 70, 40)
    assert 1 <= len(lines) <= 2
    assert size >= 40
    font = frame.load_font("de", size)
    for line in lines:
        assert font.getlength(line) <= box
    assert " ".join(lines) == text


def test_short_caption_single_line_at_max_size():
    size, lines = frame.layout_caption("Dark mode", "en", 1000, 70, 40)
    assert (size, lines) == (70, ["Dark mode"])


def test_caption_that_cannot_fit_raises():
    text = "Supercalifragilisticexpialidocious " * 12
    with pytest.raises(ShotError, match="caption"):
        frame.layout_caption(text.strip(), "en", 600, 70, 40)
    # one unbreakable word wider than the box also fails rather than clipping
    with pytest.raises(ShotError):
        frame.layout_caption("Donaudampfschifffahrtsgesellschaftskapitän", "de", 300, 70, 60)


CAPTIONS = {
    "ar": "ادرس الكتاب المقدس بلغتك",
    "iw": "לימוד מקרא בשפה שלך",
    "zh-rCN": "用你自己的标签高亮经文",
    "zh-rTW": "用你自己的標籤標示經文",
    "ja": "自分のラベルでハイライト",
    "ko": "나만의 라벨로 강조하기",
    "th": "ศึกษาพระคัมภีร์ในภาษาของคุณ",
    "hi": "अपने लेबल से चिह्नित करें",
    "bn": "নিজের লেবেল দিয়ে চিহ্নিত করুন",
    "ta": "உங்கள் லேபிள்களுடன் குறிக்கவும்",
    "te": "మీ స్వంత లేబుల్స్‌తో గుర్తించండి",
    "ur": "اپنے لیبلز کے ساتھ نمایاں کریں",
    "ru": "Выделяйте своими метками",
    "el": "Επισημάνετε με δικές σας ετικέτες",
    "vi": "Đánh dấu bằng nhãn của riêng bạn",
}


def _cmap(spec):
    if spec.path.suffix == ".ttc":
        return TTCollection(str(spec.path)).fonts[spec.index].getBestCmap()
    return TTFont(str(spec.path)).getBestCmap()


@pytest.mark.parametrize("lang", list(CAPTIONS))
def test_rtl_and_cjk_captions_render(lang):
    text = CAPTIONS[lang]
    spec = frame.font_for(lang)
    cmap = _cmap(spec)
    missing = [c for c in text if not c.isspace() and ord(c) not in cmap]
    assert not missing, f"{lang}: {spec.path.name} lacks {missing}"
    size, lines = frame.layout_caption(text, lang, 1000, 70, 40)
    img = Image.new("L", (1000, 120), 0)
    frame.draw_caption_lines(ImageDraw.Draw(img), lines, lang, size, 1000, 10, fill=255)
    assert img.getbbox() is not None


@pytest.mark.skipif(not features.check("raqm"), reason="Pillow lacks libraqm: Arabic/Indic shaping needs it (run.sh checks)")
def test_arabic_is_shaped():
    text = CAPTIONS["ar"]
    spec = frame.font_for("ar")
    shaped = ImageFont.truetype(str(spec.path), 60, layout_engine=ImageFont.Layout.RAQM)
    basic = ImageFont.truetype(str(spec.path), 60, layout_engine=ImageFont.Layout.BASIC)
    assert shaped.getlength(text, direction="rtl", language="ar") != basic.getlength(text)
    assert frame.load_font("ar", 60).getlength(text) == pytest.approx(shaped.getlength(text, direction="rtl", language="ar"))
