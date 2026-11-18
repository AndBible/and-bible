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
        placeholder_subtitles=('de"quoted', "fi\\slash", "sv$price"),
        playlist_id=None,
        episodes=(("tutorial-quoted", "abcDEF12345", ('de"quoted', "fi\\slash", "sv$price")),
                  ("tutorial-unpublished", None, ())),
    )
    rendered = app_tutorials.render(catalog)
    assert 'val placeholderId: String? = null' in rendered
    assert 'setOf("de\\"quoted", "fi\\\\slash", "sv\\$price")' in rendered
    assert '"tutorial-quoted" to Video("abcDEF12345", setOf("de\\"quoted", "fi\\\\slash", "sv\\$price"))' in rendered
    assert '"tutorial-unpublished" to Video(null, setOf())' in rendered
    assert 'val playlistId: String? = null' in rendered


def test_subtitles_must_be_sorted_and_unique(tmp_path):
    source = tmp_path / "tutorials.yaml"
    source.write_text("placeholder: null\nplaceholder_subtitles: [fi, de]\nepisodes: []\n", encoding="utf-8")
    with pytest.raises(ValueError, match="sorted and unique"):
        app_tutorials.build(source)


def test_generated_file_is_up_to_date():
    expected = app_tutorials.render(app_tutorials.build())
    assert app_tutorials.OUTPUT.read_text(encoding="utf-8") == expected, "run: cd website && uv run python -m sitegen.app_tutorials"


def test_check_mode_fails_when_generated_file_is_stale(monkeypatch, tmp_path):
    output = tmp_path / "TutorialVideoCatalog.kt"
    output.write_text("stale", encoding="utf-8")
    monkeypatch.setattr(app_tutorials, "OUTPUT", output)
    assert app_tutorials.main(["--check"]) == 1
