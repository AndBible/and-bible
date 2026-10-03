import pytest

from sitegen.videos import load, related


def write(tmp_path, text):
    p = tmp_path / "videos.yaml"
    p.write_text(text)
    return p


GOOD = """\
- {id: abcDEF12345, title: Bookmarks intro, topic: Bookmarks & StudyPads, docs: bookmarks}
- {id: shortID1234, title: Quick tip, topic: Getting started, short: true}
"""


def test_load_and_related(tmp_path):
    vids = load(write(tmp_path, GOOD), {"bookmarks"})
    assert [v.id for v in vids] == ["abcDEF12345", "shortID1234"]
    assert vids[1].short
    assert related(vids) == {"bookmarks": [("abcDEF12345", "Bookmarks intro")]}


def test_missing_file_gives_empty_catalog(tmp_path):
    assert load(tmp_path / "nope.yaml", set()) == []


@pytest.mark.parametrize("bad, message", [
    (GOOD.replace("shortID1234", "abcDEF12345"), "duplicate"),
    (GOOD.replace("Getting started", "Misc"), "topic"),
    (GOOD.replace("docs: bookmarks", "docs: nosuchpage"), "nosuchpage"),
    (GOOD.replace("abcDEF12345", "short"), "id"),
])
def test_invalid_catalog_rejected(tmp_path, bad, message):
    with pytest.raises(ValueError, match=message):
        load(write(tmp_path, bad), {"bookmarks"})



def test_page_renders_sections_in_topic_order(tmp_path):
    from sitegen import home
    from sitegen.i18n import strings
    from sitegen.paths import CONTENT
    from sitegen.videos import render_videos

    vids = load(write(tmp_path, GOOD + "- {id: devDIARY123, title: Diary, topic: Developer diaries}\n"), {"bookmarks"})
    written = render_videos(home.environment(), strings(CONTENT, "en"), vids, tmp_path)
    html = (tmp_path / "videos" / "index.html").read_text()
    assert written == ["/videos/"]
    assert html.index("Getting started") < html.index("Bookmarks &amp; StudyPads") < html.index("Developer diaries")
    assert "Customisation" not in html.split("<main")[1]  # empty topics are skipped
    assert 'data-yt-id="shortID1234"' in html and "yt--short" in html


def test_built_videos_page_groups_by_topic():
    from sitegen import paths
    html = (paths.SITE / "videos" / "index.html").read_text()
    assert html.index("Getting started") < html.index("Developer diaries")
    assert 'data-yt-id="' in html


def test_catalog_ids_have_thumbnails_and_docs_links_resolve():
    from sitegen import paths
    from sitegen.docs import published_pages

    pages = {p.removesuffix(".md") for p in published_pages(paths.WEBSITE / "zensical.toml")}
    vids = load(paths.DATA / "videos.yaml", pages)
    assert vids, "catalog is empty"
    if paths.MEDIA.is_dir():
        missing = [v.id for v in vids if not (paths.MEDIA / "videos" / f"{v.id}.webp").is_file()]
        assert not missing, f"run `make site-thumbs`: {missing}"


def test_catalog_ids_are_in_thumbnail_references(tmp_path):
    from sitegen import paths
    from sitegen.thumbnails import referenced_ids

    ids = referenced_ids(paths.CONTENT, write(tmp_path, GOOD))
    assert {"abcDEF12345", "shortID1234"} <= ids


def test_yt_seed_parses_channel_page_shapes():
    from sitegen.migrate.yt_seed import continuations, initial_data, videos_in

    data = {"a": [
        {"videoId": "aaaaaaaaaaa", "title": {"runs": [{"text": "Old "}, {"text": "style"}]}},
        {"contentType": "LOCKUP_CONTENT_TYPE_VIDEO", "contentId": "bbbbbbbbbbb",
         "metadata": {"lockupMetadataViewModel": {"title": {"content": "Lockup"}}}},
        {"onTap": {"innertubeCommand": {"reelWatchEndpoint": {"videoId": "ccccccccccc"}}},
         "overlayMetadata": {"primaryText": {"content": "A short"}}},
        {"videoId": "ddddddddddd", "thumbnail": {}},  # no title: not a video entry
        {"continuationCommand": {"token": "TOK"}},
    ]}
    assert videos_in(data) == [("aaaaaaaaaaa", "Old style"), ("bbbbbbbbbbb", "Lockup"), ("ccccccccccc", "A short")]
    assert continuations(data) == ["TOK"]
    page = 'x var ytInitialData = {"k": {"v": 1}};</script>"INNERTUBE_API_KEY":"KEY"'
    assert initial_data(page)[:2] == ({"k": {"v": 1}}, "KEY")


def test_docs_link_on_a_short_is_rejected(tmp_path):
    bad = "- {id: shortID1234, title: S, topic: Getting started, docs: bookmarks, short: true}\n"
    with pytest.raises(ValueError, match="short"):
        load(write(tmp_path, bad), {"bookmarks"})


def test_yt_seed_explains_a_page_without_data():
    from sitegen.migrate.yt_seed import initial_data

    with pytest.raises(RuntimeError, match="ytInitialData"):
        initial_data("<html>consent.youtube.com</html>")


def test_listing_cards_share_one_shape(tmp_path):
    import re
    from sitegen import home
    from sitegen.i18n import strings
    from sitegen.paths import CONTENT
    from sitegen.videos import render_videos

    vids = load(write(tmp_path, GOOD), {"bookmarks"})
    render_videos(home.environment(), strings(CONTENT, "en"), vids, tmp_path)
    html = (tmp_path / "videos" / "index.html").read_text()
    cards = re.findall(r'<div class="(yt [^"]*)" data-yt-id="([^"]+)">', html)
    assert len(cards) == 2 and all("yt--card" in c for c, _ in cards)
    short = html.split('data-yt-id="shortID1234"')[1]
    regular = html.split('data-yt-id="abcDEF12345"')[1].split('data-yt-id="shortID1234"')[0]
    assert 'class="yt__backdrop"' in short and "yt__backdrop" not in regular


def test_clip_and_short_share_one_grid_in_catalog_order(tmp_path):
    from sitegen import home
    from sitegen.i18n import strings
    from sitegen.paths import CONTENT
    from sitegen.videos import render_videos

    text = ("- {id: shortFIRST1, title: S1, topic: Getting started, short: true}\n"
            "- {id: clipMIDDLE1, title: C1, topic: Getting started}\n"
            "- {id: shortLAST12, title: S2, topic: Getting started, short: true}\n")
    render_videos(home.environment(), strings(CONTENT, "en"), load(write(tmp_path, text), set()), tmp_path)
    html = (tmp_path / "videos" / "index.html").read_text()
    assert "video-grid--shorts" not in html and html.count('class="video-grid"') == 1
    grid = html.split('class="video-grid"')[1].split("</section>")[0]
    assert grid.index("shortFIRST1") < grid.index("clipMIDDLE1") < grid.index("shortLAST12")
