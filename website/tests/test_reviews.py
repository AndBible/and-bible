import re
from html import unescape
from pathlib import Path

import pytest

from sitegen import paths
from sitegen.build import build
from sitegen.reviews import load

SITE_YAML = paths.WEBSITE / "content" / "en" / "site.yaml"


def _write(tmp_path: Path, body: str) -> Path:
    p = tmp_path / "reviews.yaml"
    p.write_text(body, encoding="utf-8")
    return p


def test_real_catalog_loads():
    reviews = load(paths.DATA / "reviews.yaml")
    assert len(reviews) == 6
    assert all(r.text and isinstance(r.year, int) for r in reviews)


def test_missing_file_is_no_reviews(tmp_path):
    assert load(tmp_path / "nope.yaml") == []


@pytest.mark.parametrize("body", [
    "- {year: 2021, text: ''}\n",
    "- {year: 2021}\n",
    "- {year: '2021', text: Hi}\n",
    "- {text: Hi}\n",
    "- {author: A, year: 2021, text: Hi}\n",
    "[]\n",
    "author: A\n",
])
def test_invalid_entries_rejected(tmp_path, body):
    with pytest.raises(ValueError):
        load(_write(tmp_path, body))


def test_home_renders_every_review(tmp_path):
    content = tmp_path / "content"
    (content / "en").mkdir(parents=True)
    (content / "en" / "site.yaml").write_text(SITE_YAML.read_text())
    data = tmp_path / "data"
    data.mkdir()
    (data / "reviews.yaml").write_text((paths.DATA / "reviews.yaml").read_text(encoding="utf-8"), encoding="utf-8")
    out = tmp_path / "out"
    build(content, out, data=data, docs=False)
    raw = (out / "index.html").read_text()
    html = unescape(raw)
    reviews = load(paths.DATA / "reviews.yaml")
    for r in reviews:
        assert r.text in html
    figures = re.findall(r"<figure class=\"review\">.*?</figure>", raw, re.S)
    assert len(figures) == len(reviews)
    for fig in figures:
        assert fig.count("★") == 5
        assert 'aria-label="5 out of 5 stars"' in fig
        assert '<span aria-hidden="true">★★★★★</span>' in fig
    for name in ("Timmy Braun", "K Nance", "Jacques Stander", "Robert Aroney", "Purejoy Sadguru", "Eric Bradshaw"):
        assert name not in html
    assert all(f"Google Play review, {r.year}</figcaption>" in raw for r in reviews)
    assert "showAllReviews=true" in html
