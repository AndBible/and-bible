"""TutorialVideoCatalog.kt is generated from the public tutorial catalog."""

from __future__ import annotations

import pytest

from sitegen import app_tutorials


def test_catalog_reads_placeholder_and_episode_metadata(tmp_path):
    source = tmp_path / "tutorials.yaml"
    source.write_text('''\
placeholder: RlLkpKZZwTg
placeholder_subtitles: [de, fi, sv]
playlist: PL12345
episodes: []
''', encoding="utf-8")
    catalog = app_tutorials.build(source)
    assert catalog.placeholder_id == "RlLkpKZZwTg"
    assert catalog.placeholder_subtitles == ("de", "fi", "sv")
    assert catalog.playlist_id == "PL12345"


def test_render_metadata_and_escape_special_characters():
    catalog = app_tutorials.Catalog(
        placeholder_id=None,
        placeholder_subtitles=('de"quoted', "fi\\slash", "sv$price", "fr\nline"),
        playlist_id=None,
        episodes=(("tutorial-quoted", "abcDEF12345", ('de"quoted', "fi\\slash", "sv$price", "fr\nline")),
                  ("tutorial-unpublished", None, ())),
    )
    rendered = app_tutorials.render(catalog)
    assert 'val placeholderId: String? = null' in rendered
    assert 'setOf("de\\"quoted", "fi\\\\slash", "sv\\$price", "fr\\nline")' in rendered
    assert '"tutorial-quoted" to Video("abcDEF12345", setOf("de\\"quoted", "fi\\\\slash", "sv\\$price", "fr\\nline"))' in rendered
    assert '"tutorial-unpublished" to Video(null, setOf())' in rendered
    assert 'val playlistId: String? = null' in rendered


@pytest.mark.parametrize("languages, message", [
    ("[fi, de]", "sorted and unique"),
    ("[not_a_language]", "bad subtitle language"),
    ("[en]", "lists English"),
])
def test_subtitles_are_normalized_and_validated(tmp_path, languages, message):
    source = tmp_path / "tutorials.yaml"
    source.write_text(f"placeholder: null\nplaceholder_subtitles: {languages}\nepisodes: []\n", encoding="utf-8")
    with pytest.raises(ValueError, match=message):
        app_tutorials.build(source)


def test_android_subtitle_aliases_normalize_without_changing_language_tags(tmp_path):
    source = tmp_path / "tutorials.yaml"
    source.write_text("placeholder: null\nplaceholder_subtitles: [pt-rBR, b+sr+Latn]\nepisodes: []\n", encoding="utf-8")
    assert app_tutorials.build(source).placeholder_subtitles == ("pt-BR", "sr-Latn")


def test_episode_slugs_must_be_valid_and_unique(tmp_path):
    source = tmp_path / "tutorials.yaml"
    for slugs, error in ((["Bad_Slug"], "bad episode slug"), (["first", "first"], "duplicate episode slug")):
        source.write_text("placeholder: null\nepisodes:\n" + "".join(f"  - {{slug: {slug}, youtube: null, subtitles: []}}\n" for slug in slugs), encoding="utf-8")
        with pytest.raises(ValueError, match=error):
            app_tutorials.build(source)


def test_generated_file_is_up_to_date():
    expected = app_tutorials.render(app_tutorials.build())
    assert app_tutorials.OUTPUT.read_text(encoding="utf-8") == expected, "run: cd website && uv run python -m sitegen.app_tutorials"


def test_check_mode_fails_when_generated_file_is_stale(monkeypatch, tmp_path):
    output = tmp_path / "TutorialVideoCatalog.kt"
    output.write_text("stale", encoding="utf-8")
    monkeypatch.setattr(app_tutorials, "OUTPUT", output)
    assert app_tutorials.main(["--check"]) == 1
