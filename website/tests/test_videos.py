import pytest

from sitegen.videos import load, related


def write(tmp_path, text):
    p = tmp_path / "videos.yaml"
    p.write_text(text)
    return p


GOOD = """\
- {id: abcDEF12345, title: Bookmarks intro, topic: Bookmarks & StudyPads, docs: bookmarks, published: 2025-01-02}
- {id: shortID1234, title: Quick tip, topic: Getting started, short: true, published: "2025-03-04"}
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



@pytest.mark.parametrize("bad", [
    GOOD.replace(", published: 2025-01-02", ""),
    GOOD.replace("published: 2025-01-02", "published: yesterday"),
    GOOD.replace("published: 2025-01-02", "published: 2025-13-40"),
    GOOD.replace("published: 2025-01-02", 'published: "20250102"'),
    GOOD.replace("published: 2025-01-02", "published: 2999-01-01"),
])
def test_missing_invalid_or_future_published_rejected(tmp_path, bad):
    with pytest.raises(ValueError, match="published"):
        load(write(tmp_path, bad), {"bookmarks"})


def test_published_parsed_from_yaml_date_and_quoted_string(tmp_path):
    vids = load(write(tmp_path, GOOD), {"bookmarks"})
    assert [v.published.isoformat() for v in vids] == ["2025-01-02", "2025-03-04"]


def test_newest_orders_by_date_and_keeps_catalog_order_on_ties(tmp_path):
    from sitegen.videos import newest
    text = "".join(f"- {{id: vid{i:08d}, title: T{i}, topic: Getting started, published: {d}}}\n"
                   for i, d in enumerate(["2024-01-01", "2025-06-01", "2025-06-01", "2023-01-01", "2025-07-01"]))
    vids = load(write(tmp_path, text), set())
    assert [v.title for v in newest(vids)] == ["T4", "T1", "T2"]
    assert len(newest(vids, 10)) == 5 and newest([]) == []


def test_page_renders_sections_in_topic_order(tmp_path):
    from sitegen import home
    from sitegen.i18n import strings
    from sitegen.paths import CONTENT
    from sitegen.videos import render_videos

    vids = load(write(tmp_path, GOOD + "- {id: devDIARY123, title: Diary, topic: Developer diaries, published: 2024-05-06}\n"), {"bookmarks"})
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
    bad = "- {id: shortID1234, title: S, topic: Getting started, docs: bookmarks, short: true, published: 2025-01-01}\n"
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

    text = ("- {id: shortFIRST1, title: S1, topic: Getting started, short: true, published: 2025-01-01}\n"
            "- {id: clipMIDDLE1, title: C1, topic: Getting started, published: 2025-01-01}\n"
            "- {id: shortLAST12, title: S2, topic: Getting started, short: true, published: 2025-01-01}\n")
    render_videos(home.environment(), strings(CONTENT, "en"), load(write(tmp_path, text), set()), tmp_path)
    html = (tmp_path / "videos" / "index.html").read_text()
    assert "video-grid--shorts" not in html and html.count('class="video-grid"') == 1
    grid = html.split('class="video-grid"')[1].split("</section>")[0]
    assert grid.index("shortFIRST1") < grid.index("clipMIDDLE1") < grid.index("shortLAST12")


def test_version_is_optional_and_kept_as_text(tmp_path):
    text = GOOD.replace("published: 2025-01-02", 'published: 2025-01-02, version: "5.1"')
    vids = load(write(tmp_path, text), {"bookmarks"})
    assert [v.version for v in vids] == ["5.1", None]


@pytest.mark.parametrize("bad", ['"5"', '"5.1.1117"', '"v5.1"', "5.1", '""', '"5.x"'])
def test_malformed_version_rejected(tmp_path, bad):
    text = GOOD.replace("published: 2025-01-02", f"published: 2025-01-02, version: {bad}")
    with pytest.raises(ValueError, match="version"):
        load(write(tmp_path, text), {"bookmarks"})


def test_catalog_versions_never_precede_their_release_or_decrease_with_date():
    from datetime import date
    from sitegen import paths
    vids = [v for v in load(paths.DATA / "videos.yaml", set(
        p.removesuffix(".md") for p in __import__("sitegen.docs", fromlist=["x"]).published_pages(
            paths.WEBSITE / "zensical.toml"))) if v.version]
    key = lambda v: tuple(int(x) for x in v.version.split("."))  # noqa: E731
    ordered = sorted(vids, key=lambda v: v.published)
    assert all(key(a) <= key(b) for a, b in zip(ordered, ordered[1:]))
    assert min(v.published for v in vids) >= date(2018, 1, 1)
