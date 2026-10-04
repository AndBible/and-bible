"""Topbar GitHub facts: formatting, refresh, and what the built pages show."""

import json

import pytest

from sitegen import github
from sitegen.home import environment


@pytest.mark.parametrize("count,text", [(0, "0"), (816, "816"), (999, "999"), (1000, "1k"), (1234, "1.2k"), (12000, "12k")])
def test_format_stars(count, text):
    assert github.format_stars(count) == text


def test_load_missing_file_is_empty(tmp_path):
    assert github.load(tmp_path / "none.yaml") == {}


def test_load_rejects_incomplete_file(tmp_path):
    bad = tmp_path / "g.yaml"
    bad.write_text("stars: lots\nlicense: GPL-3.0\n")
    with pytest.raises(ValueError):
        github.load(bad)


def test_refresh_writes_what_load_reads(tmp_path):
    path = tmp_path / "g.yaml"
    answer = json.dumps({"stargazers_count": 1500, "license": {"spdx_id": "GPL-3.0"}}).encode()
    github.refresh(path, fetcher=lambda url: answer)
    assert github.load(path) == {"stars": "1.5k", "license": "GPL-3.0"}


def test_refresh_refuses_unusable_answer(tmp_path):
    path = tmp_path / "g.yaml"
    answer = json.dumps({"message": "rate limited"}).encode()
    with pytest.raises(ValueError):
        github.refresh(path, fetcher=lambda url: answer)
    assert not path.exists()


def _topbar(**github_facts):
    env = environment()
    env.globals["github"] = github_facts
    strings = {"site_name": "AndBible", "meta": {"image_alt": ""},
               "nav": {"blog": "Blog", "docs": "Docs", "videos": "Videos", "support": "Support", "github": "GitHub",
                       "github_stars": "Stars", "github_license": "Licence", "theme_toggle": "t", "menu": "m"},
               "sections": {"support_url": "/s"}, "footer": {"source_url": "https://github.com/x", "source": "s",
                                                             "issues_url": "i", "issues": "i"}}
    return env.get_template("base.html").render(strings=strings, lang="en", prefix="", title="t", description="d",
                                                canonical="c", og_type="website", body_class="")


def test_topbar_shows_stars_and_licence():
    html = _topbar(stars="816", license="GPL-3.0")
    assert "816" in html and "GPL-3.0" in html


def test_topbar_without_facts_still_links_github():
    html = _topbar()
    assert "nav-github__facts" not in html and 'href="https://github.com/x"' in html
