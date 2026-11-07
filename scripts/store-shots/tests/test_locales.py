from pathlib import Path

import pytest

from storeshots.locales import ANDROID_LOCALES, IOS_LOCALES, locale_for

REPO = Path(__file__).resolve().parents[3]
FASTLANE = REPO / "fastlane" / "metadata" / "android"

# Locales that have no Play listing folder under fastlane/metadata/android/.
NO_PLAY_FOLDER = {"b+sr+Latn"}


def test_android_has_49_entries_incl_en():
    assert len(ANDROID_LOCALES) == 49
    assert {l.app for l in ANDROID_LOCALES} >= {
        "en", "fi", "ar", "iw", "in", "b+sr+Latn", "zh-rCN", "zh-rTW", "pt-rBR"}
    assert len({l.app for l in ANDROID_LOCALES}) == 49


def test_tiers():
    tiers = {}
    for l in ANDROID_LOCALES:
        tiers.setdefault(l.tier, []).append(l.app)
    assert len(tiers[0]) == 1 and len(tiers[1]) == 30 and len(tiers[2]) == 18


def test_play_codes_exist_in_fastlane():
    folders = {p.name for p in FASTLANE.iterdir() if p.is_dir()}
    for l in ANDROID_LOCALES:
        if l.play is None:
            assert l.app in NO_PLAY_FOLDER, f"{l.app} has no play folder but is not listed"
        else:
            assert l.play in folders, f"{l.app}: {l.play} missing under fastlane"
    assert {l.app for l in ANDROID_LOCALES if l.play is None} == NO_PLAY_FOLDER


def test_ios_list():
    assert [l.appstore for l in IOS_LOCALES] == [
        "en-US", "de-DE", "fr-FR", "es-ES", "it", "nl-NL", "pt-BR", "sv", "fi", "pl", "ko", "zh-Hans"]
    assert [l.app for l in IOS_LOCALES] == [
        "en", "de", "fr", "es", "it", "nl", "pt-rBR", "sv", "fi", "pl", "ko", "zh-rCN"]


def test_bcp47_tags():
    expected = {"iw": "he", "in": "id", "b+sr+Latn": "sr-Latn", "b+sr+RS": "sr-RS",
                "zh-rCN": "zh-CN", "zh-rTW": "zh-TW", "pt-rBR": "pt-BR", "fi": "fi", "en": "en"}
    for app, tag in expected.items():
        assert locale_for(app).bcp47 == tag


def test_unknown_code_raises():
    with pytest.raises(KeyError, match="xx"):
        locale_for("xx")
