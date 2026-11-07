"""The release channel switch: site.yaml `release:` drives the landing download entries and docs blocks."""

import re
from pathlib import Path

import pytest
import yaml

from sitegen import channel, paths
from sitegen.build import build
from sitegen.channel import PLAY_BETA, RELEASES_LATEST, RELEASES_PAGE, Release, apply, getapp, settings
from sitegen.i18n import strings

SITE_YAML = Path(__file__).resolve().parents[1] / "content" / "en" / "site.yaml"
TESTFLIGHT = "https://testflight.apple.com/join/AbCdEf12"
APP_STORE = "https://apps.apple.com/us/app/andbible-bible-study/id6774904527"
BETA = Release(channel="beta")
BETA_TF = Release(channel="beta", ios_beta_link=TESTFLIGHT, ios_beta_enabled=True)
STABLE = Release(channel="stable")


def _site(**release):
    site = yaml.safe_load(SITE_YAML.read_text())
    site["release"] = {"channel": "stable", "ios_beta_link": "", "ios_beta_enabled": False,
                       "beta_release_link": ""} | release
    return site


# --- settings ------------------------------------------------------------------------------

def test_site_yaml_has_a_valid_release_section():
    release = settings(yaml.safe_load(SITE_YAML.read_text()))
    assert release.channel in channel.CHANNELS


def test_missing_release_section_means_stable():
    assert settings({}) == STABLE


@pytest.mark.parametrize("release", [{"channel": "rc"}, {"channel": "beta", "ios_beta_link": "http://x"},
                                     {"channel": "beta", "beta_release_link": "github.com/x"}])
def test_invalid_release_settings_fail(release):
    with pytest.raises(ValueError):
        settings({"release": release})


def test_testflight_only_counts_in_beta():
    assert BETA_TF.testflight and not BETA.testflight
    assert not Release(channel="stable", ios_beta_link=TESTFLIGHT, ios_beta_enabled=True).testflight
    assert not Release(channel="beta", ios_beta_link=TESTFLIGHT).testflight  # link set, beta switched off


def test_ios_beta_condition_ignores_the_channel():
    text = "<!-- if ios_beta -->\n[TF]({{ testflight }})\n<!-- else -->\nsoon\n<!-- end -->\n"
    on = {"ios_beta_link": TESTFLIGHT, "ios_beta_enabled": True}
    assert apply(text, Release(channel="stable", **on)) == apply(text, Release(channel="beta", **on)) \
        == f"[TF]({TESTFLIGHT})\n"
    assert apply(text, Release(channel="beta", ios_beta_link=TESTFLIGHT)) == "soon\n"


def test_ios_beta_enabled_needs_a_link():
    with pytest.raises(ValueError, match="ios_beta_link"):
        settings({"release": {"channel": "beta", "ios_beta_enabled": True}})
    with pytest.raises(ValueError, match="true or false"):
        settings({"release": {"channel": "beta", "ios_beta_enabled": "yes"}})


# --- docs blocks ---------------------------------------------------------------------------

PAGE = """A
<!-- if beta -->
beta {{ beta_release }}
<!-- if testflight -->
tf {{ testflight }}
<!-- else -->
tf soon
<!-- end -->
<!-- else -->
stable {{ releases_latest }}
<!-- end -->
<!-- if not beta -->
not beta
<!-- end -->
Z
"""


def test_apply_stable_keeps_only_stable_text():
    assert apply(PAGE, STABLE) == f"A\nstable {RELEASES_LATEST}\nnot beta\nZ\n"


def test_apply_beta_without_testflight():
    assert apply(PAGE, BETA) == f"A\nbeta {RELEASES_PAGE}\ntf soon\nZ\n"


def test_apply_beta_with_testflight_and_a_beta_release_link():
    release = Release(channel="beta", ios_beta_link=TESTFLIGHT, ios_beta_enabled=True,
                      beta_release_link="https://github.com/AndBible/and-bible/releases/tag/production-1260")
    assert apply(PAGE, release) == f"A\nbeta {release.beta_release_link}\ntf {TESTFLIGHT}\nZ\n"


def test_text_without_markers_is_unchanged():
    text = "# T {#t}\n\n[x](y.md){ height=\"4\" }\n"
    assert apply(text, BETA) == text == apply(text, STABLE)


@pytest.mark.parametrize("text", ["<!-- if beta -->\nx\n", "x\n<!-- end -->\n", "<!-- else -->\n",
                                  "<!-- if gamma -->\nx\n<!-- end -->\n", "a <!-- if beta --> b\n",
                                  "{{ nonsense }}\n",
                                  "<!-- if beta -->\na\n<!-- else -->\nb\n<!-- else -->\nc\n<!-- end -->\n"])
def test_malformed_markers_fail(text):
    with pytest.raises(ValueError):
        apply(text, BETA)


def test_fenced_code_is_left_alone():
    text = "```\n<!-- if beta -->\n{{ anything }}\n<!-- end -->\n```\n~~~md\n{{ x }}\n~~~\n"
    assert apply(text, BETA) == text == apply(text, STABLE)


def test_placeholders_in_hidden_branches_are_not_checked():
    text = "<!-- if testflight -->\n[TF]({{ testflight }})\n<!-- end -->\nend\n"
    assert apply(text, BETA) == apply(text, STABLE) == "end\n"


def test_nested_beta_block_vanishes_in_stable():
    assert apply("<!-- if beta -->\n<!-- if not testflight -->\nsoon\n<!-- end -->\n<!-- end -->\nZ\n", STABLE) == "Z\n"


def test_a_non_string_when_is_a_clear_error():
    site = _site(channel="beta")
    site["getapp"]["beta"]["stores"][0]["when"] = True
    with pytest.raises(ValueError, match="when"):
        getapp(site)


def test_an_empty_testflight_placeholder_fails_instead_of_linking_nowhere():
    with pytest.raises(ValueError, match="testflight"):
        apply("[TestFlight]({{ testflight }})\n", BETA)


# --- landing entries -----------------------------------------------------------------------

def _urls(entries):
    return {e["name"]: e.get("url") for e in entries}


def test_stable_landing_is_the_plain_store_list():
    site = _site(channel="stable")
    app = getapp(site)
    assert app["stores"] == site["getapp"]["stores"] and app["others"] == site["getapp"]["others"]
    assert "banner" not in app and "beta" not in app
    stores = _urls(app["stores"])
    assert stores["Windows"] == stores["Linux"] == RELEASES_LATEST
    assert stores["App Store"] == APP_STORE
    assert "beta" not in yaml.safe_dump(app["stores"]).lower()


def test_beta_landing_links_reach_the_beta_assets():
    app = getapp(_site(channel="beta"))
    stores = _urls(app["stores"])
    assert stores["Windows"] == stores["Linux"] == stores["GitHub"] == RELEASES_PAGE
    assert stores["Google Play"] == PLAY_BETA
    assert "TestFlight" not in stores
    assert stores["App Store"] == APP_STORE
    notes = {e["name"]: e["note"] for e in app["stores"]}
    assert "soon" in notes["App Store"]
    for name in ("Google Play", "Windows", "Linux", "GitHub"):
        assert "beta" in notes[name], name
    assert "releases/latest" not in "".join(stores.values())
    assert app["banner"] and "beta" in app["banner"]
    assert all("{{" not in (e.get("url") or "") for e in app["stores"] + app["others"])
    assert _urls(app["others"])["GitHub"] == RELEASES_LATEST  # the stable APK stays one click away


def test_beta_landing_with_testflight_and_a_release_link():
    tag = "https://github.com/AndBible/and-bible/releases/tag/production-1260"
    app = getapp(_site(channel="beta", ios_beta_link=TESTFLIGHT, ios_beta_enabled=True, beta_release_link=tag))
    stores = _urls(app["stores"])
    assert stores["TestFlight"] == TESTFLIGHT and "App Store" not in stores
    assert _urls(app["others"])["App Store"] == APP_STORE
    assert stores["Windows"] == stores["Linux"] == tag


@pytest.fixture
def content(tmp_path):
    en = tmp_path / "content" / "en"
    en.mkdir(parents=True)
    return tmp_path / "content"


def _home(content, tmp_path, **release):
    (content / "en" / "site.yaml").write_text(yaml.safe_dump(_site(**release), allow_unicode=True))
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    html = (out / "index.html").read_text()
    return html.split('class="getapp__bar"')[1].split('class="phone"')[0]


def test_home_renders_the_beta_entries(content, tmp_path):
    bar = _home(content, tmp_path, channel="beta")
    assert f'href="{PLAY_BETA}"' in bar and f'href="{RELEASES_PAGE}"' in bar
    assert 'class="getapp__beta"' in bar and 'href="/docs/releases/release_6_0/"' in bar
    assert "Stable version on" in bar and f'href="{RELEASES_LATEST}"' in bar


def test_home_renders_the_stable_entries(content, tmp_path):
    bar = _home(content, tmp_path, channel="stable")
    assert PLAY_BETA not in bar and "getapp__beta" not in bar
    assert f'href="{RELEASES_LATEST}"' in bar and "Also on" in bar


def test_beta_banner_links_an_existing_post_and_docs_page():
    banner = yaml.safe_load(SITE_YAML.read_text())["getapp"]["beta"]["banner"]
    targets = re.findall(r"\]\((/[^)]*)\)", banner)
    assert "/docs/releases/release_6_0/" in targets
    posts = {f"/{p.name[:10].replace('-', '/')}/" + yaml.safe_load(p.read_text().split("---")[1])["slug"] + "/"
             for p in (paths.CONTENT / "en" / "blog").glob("*.md")}
    for target in targets:
        assert target.startswith("/docs/") or target in posts, target


def test_strings_still_merge_for_translations(content):
    (content / "en" / "site.yaml").write_text(SITE_YAML.read_text())
    assert strings(content, "en")["release"]["channel"] in channel.CHANNELS


# --- docs in both channels -----------------------------------------------------------------

STABLE_PAGES = ("getting_started", "faq", "desktop", "discrete_build", "releases/release_6_0", "releases")


def _docs(tmp_path_factory, **release):
    from sitegen.docs import build_docs
    out = tmp_path_factory.mktemp("docs")
    build_docs(paths.CONTENT, out, {}, release={"channel": "stable"} | release)
    return lambda page: (out / "docs" / page / "index.html").read_text()


@pytest.fixture(scope="module")
def stable_docs(tmp_path_factory):
    return _docs(tmp_path_factory, channel="stable")


@pytest.fixture(scope="module")
def beta_docs(tmp_path_factory):
    return _docs(tmp_path_factory, channel="beta")


@pytest.fixture(scope="module")
def testflight_docs(tmp_path_factory):
    return _docs(tmp_path_factory, channel="beta", ios_beta_link=TESTFLIGHT, ios_beta_enabled=True)


def _text(html):
    return re.sub(r"<[^>]+>", " ", html.split('<article', 1)[1].split("</article>", 1)[0])


@pytest.mark.parametrize("page", STABLE_PAGES)
def test_stable_docs_do_not_mention_the_beta(stable_docs, page):
    html = stable_docs(page)
    assert "beta" not in _text(html).lower(), page
    assert PLAY_BETA not in html and "testflight" not in html.lower() and "<!--" not in _text(html)


def test_stable_docs_link_the_latest_release(stable_docs):
    for page in ("getting_started", "desktop", "discrete_build", "faq"):
        assert f'href="{RELEASES_LATEST}"' in stable_docs(page), page
    assert re.search(r"<h1[^>]*>Release 6\.0<", stable_docs("releases/release_6_0"))


def test_beta_docs_send_desktop_and_android_users_to_the_beta(beta_docs):
    gs = beta_docs("getting_started")
    assert f'href="{PLAY_BETA}"' in gs
    windows = gs.split('id="windows"')[1].split('id="linux"')[0]
    linux = gs.split('id="linux"')[1].split("<h2")[0]
    for section in (windows, linux):
        assert f'href="{RELEASES_PAGE}"' in section and "releases/latest" not in section
        assert "public beta" in section
    assert "coming soon" in gs.split('id="iphone-and-ipad"')[1].split('id="mac"')[0]
    assert "testflight.apple.com" not in gs
    desktop = beta_docs("desktop")
    assert "Public beta" in desktop and f'href="{RELEASES_PAGE}"' in desktop
    assert "releases/latest" not in desktop
    assert f'href="{RELEASES_PAGE}"' in beta_docs("discrete_build")
    assert f'href="{RELEASES_PAGE}"' in beta_docs("faq") and "coming" in beta_docs("faq")


def test_beta_marks_the_6_0_release_page(beta_docs):
    page = beta_docs("releases/release_6_0")
    assert re.search(r"<h1[^>]*>Release 6\.0 \(beta\)", page)
    assert 'id="release-6-0"' in page  # the anchor the app or other pages may link stays
    assert "7 November 2026" in page and "draft" not in _text(page)
    assert f'href="{PLAY_BETA}"' in page and f'href="{RELEASES_PAGE}"' in page
    assert "Release 6.0 (public beta)" in beta_docs("releases")
    assert "support/#reporting-issues" in page


def test_testflight_link_reaches_every_ios_mention(testflight_docs):
    for page in ("getting_started", "faq", "desktop", "releases/release_6_0"):
        html = testflight_docs(page)
        assert f'href="{TESTFLIGHT}"' in html, page
        assert "coming soon" not in _text(html), page


# --- blog posts follow the iOS beta switch -------------------------------------------------

POST = paths.CONTENT / "en" / "blog" / "2026-11-07-andbible-6-0-public-beta.md"


def _post_html(content, tmp_path, **release):
    (content / "en" / "site.yaml").write_text(yaml.safe_dump(_site(**release), allow_unicode=True))
    (content / "en" / "blog").mkdir(exist_ok=True)
    (content / "en" / "blog" / POST.name).write_text(POST.read_text())
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    return (out / "2026" / "11" / "07" / "andbible-6-0-public-beta" / "index.html").read_text()


def test_announcement_says_ios_is_coming_until_the_switch(content, tmp_path):
    html = _post_html(content, tmp_path, channel="beta", ios_beta_link=TESTFLIGHT)
    assert "coming in a few days" in html and "testflight.apple.com" not in html and "<!--" not in html


def test_announcement_links_testflight_once_switched_on_also_after_stable(content, tmp_path):
    for channel_name in ("beta", "stable"):
        html = _post_html(content, tmp_path, channel=channel_name, ios_beta_link=TESTFLIGHT, ios_beta_enabled=True)
        assert f'href="{TESTFLIGHT}"' in html and "coming in a few days" not in html, channel_name


def test_announcement_names_no_exact_version():
    assert not re.search(r"6\.0\.\d", POST.read_text())
