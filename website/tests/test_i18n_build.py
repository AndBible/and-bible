import shutil

from sitegen import paths
from sitegen.build import build


def test_fixture_language_gets_translated_page_and_english_fallback(tmp_path):
    content = tmp_path / "content"
    shutil.copytree(paths.CONTENT, content)
    (content / "xx" / "docs").mkdir(parents=True)
    (content / "xx" / "site.yaml").write_text("hero:\n  eyebrow: XX-EYEBROW\n")
    (content / "xx" / "docs" / "index.md").write_text("# XX docs home\n")
    out = tmp_path / "out"
    build(content, out)
    assert "XX-EYEBROW" in (out / "xx" / "index.html").read_text()
    assert "XX docs home" in (out / "xx" / "docs" / "index.html").read_text()
    # an untranslated docs page falls back to English
    assert (out / "xx" / "docs" / "getting_started" / "index.html").is_file()
    assert "XX-EYEBROW" not in (out / "index.html").read_text()
