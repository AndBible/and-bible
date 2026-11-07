"""Locale table: app resource code <-> Play folder <-> App Store code <-> BCP-47 tag.

Tier lists are copied from `.claude/skills/update-translations/SKILL.md` ("Language Quality
Tiers"). Tier 0 is English, the source language.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Locale:
    app: str                 # Android resource suffix, e.g. "iw", "b+sr+Latn", "zh-rCN"
    play: str | None         # folder under fastlane/metadata/android/, None if Play has none
    appstore: str | None     # App Store Connect locale, None when the iOS set is English fallback
    tier: int
    bcp47: str               # tag for `cmd locale set-app-locales` and font/caption choice


# app code -> (play folder, bcp47). Play folders are checked by tests against fastlane/.
_TABLE: dict[str, tuple[str | None, str]] = {
    "en": ("en-US", "en"),
    # tier 1
    "ar": ("ar", "ar"), "bg": ("bg", "bg"), "ca": ("ca", "ca"), "cs": ("cs-CZ", "cs"),
    "da": ("da-DK", "da"), "de": ("de-DE", "de"), "el": ("el-GR", "el"), "es": ("es-ES", "es"),
    "fi": ("fi-FI", "fi"), "fr": ("fr-FR", "fr"), "hi": ("hi-IN", "hi"), "hr": ("hr", "hr"),
    "hu": ("hu-HU", "hu"), "it": ("it-IT", "it"), "iw": ("iw-IL", "he"), "ja": ("ja-JP", "ja"),
    "ko": ("ko-KR", "ko"), "nl": ("nl-NL", "nl"), "pl": ("pl-PL", "pl"), "pt": ("pt-PT", "pt"),
    "pt-rBR": ("pt-BR", "pt-BR"), "ro": ("ro", "ro"), "ru": ("ru-RU", "ru"), "sv": ("sv-SE", "sv"),
    "th": ("th", "th"), "tr": ("tr-TR", "tr"), "uk": ("uk", "uk"), "vi": ("vi", "vi"),
    "zh-rCN": ("zh-CN", "zh-CN"), "zh-rTW": ("zh-TW", "zh-TW"),
    # tier 2 (Play has a single Serbian folder "sr": it takes the Cyrillic/RS variant)
    "af": ("af", "af"), "b+sr+Latn": (None, "sr-Latn"), "b+sr+RS": ("sr", "sr-RS"),
    "bn": ("bn-BD", "bn"), "eo": ("eo", "eo"), "et": ("et", "et"), "fil": ("fil", "fil"),
    "in": ("id", "id"), "lt": ("lt", "lt"), "ms": ("ms-MY", "ms"), "nb": ("no-NO", "nb"),
    "ne": ("ne-NP", "ne"), "sk": ("sk", "sk"), "sl": ("sl", "sl"), "sw": ("sw", "sw"),
    "ta": ("ta-IN", "ta"), "te": ("te-IN", "te"), "ur": ("ur", "ur"),
}

TIER1 = ["ar", "bg", "ca", "cs", "da", "de", "el", "es", "fi", "fr", "hi", "hr", "hu", "it", "iw", "ja",
         "ko", "nl", "pl", "pt", "pt-rBR", "ro", "ru", "sv", "th", "tr", "uk", "vi", "zh-rCN", "zh-rTW"]
TIER2 = ["af", "b+sr+Latn", "b+sr+RS", "bn", "eo", "et", "fil", "in", "lt", "ms", "nb", "ne", "sk",
         "sl", "sw", "ta", "te", "ur"]

# (app code, App Store Connect locale) in the maintainer's order.
_IOS = [("en", "en-US"), ("de", "de-DE"), ("fr", "fr-FR"), ("es", "es-ES"), ("it", "it"),
        ("nl", "nl-NL"), ("pt-rBR", "pt-BR"), ("sv", "sv"), ("fi", "fi"), ("pl", "pl"),
        ("ko", "ko"), ("zh-rCN", "zh-Hans")]
_APPSTORE = dict(_IOS)


def _make(app: str, tier: int) -> Locale:
    play, tag = _TABLE[app]
    return Locale(app, play, _APPSTORE.get(app), tier, tag)


ANDROID_LOCALES: list[Locale] = (
    [_make("en", 0)] + [_make(a, 1) for a in TIER1] + [_make(a, 2) for a in TIER2])

_BY_APP = {l.app: l for l in ANDROID_LOCALES}

IOS_LOCALES: list[Locale] = [_BY_APP[app] for app, _ in _IOS]


def locale_for(app_code: str) -> Locale:
    try:
        return _BY_APP[app_code]
    except KeyError:
        raise KeyError(f"unknown locale code: {app_code}") from None
