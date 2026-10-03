import io
import json
from datetime import date

import pytest
from PIL import Image

from sitegen import newvideo, videos
from sitegen.content import load_posts
from sitegen.newvideo import NewVideoError, build_parser, run

ID = "abcDEF12345"
HEADER = "# comment header\n"
EXISTING = HEADER + '- {id: "old00000001", title: "Old one", topic: "Getting started"}\n'


def png() -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (640, 360), "red").save(buf, "PNG")
    return buf.getvalue()


@pytest.fixture
def env(tmp_path):
    (tmp_path / "data").mkdir()
    (tmp_path / "data" / "videos.yaml").write_text(EXISTING)
    (tmp_path / "content" / "en" / "blog").mkdir(parents=True)
    return tmp_path


def call(env, *argv, title_json=None, **kw):
    args = build_parser().parse_args(list(argv))
    fetcher = kw.pop("fetcher", lambda url: json.dumps(title_json or {"title": "Hello World: A Tip"}).encode())
    return run(args, fetcher=fetcher, opener=lambda url: png(), data=env / "data", content=env / "content",
               media=env / "media", docs_pages={"windows"}, today=date(2026, 10, 3), **kw)


def base(vid=ID):
    return [vid, "--topic", "Getting started", "--summary", "Sum."]


@pytest.mark.parametrize("value,expected", [
    (f"https://www.youtube.com/watch?v={ID}", (ID, False)),
    (f"https://youtu.be/{ID}", (ID, False)),
    (f"https://www.youtube.com/shorts/{ID}", (ID, True)),
    (f"https://www.youtube.com/embed/{ID}", (ID, False)),
    (ID, (ID, False)),
])
def test_parse_video(value, expected):
    assert newvideo.parse_video(value) == expected


@pytest.mark.parametrize("value", ["", "short", "https://example.com/watch?v=" + ID, "https://youtu.be/tooshort"])
def test_parse_video_invalid(value):
    with pytest.raises(NewVideoError, match="cannot find"):
        newvideo.parse_video(value)


def test_slugify():
    assert newvideo.slugify("Café: How to use X & Y!") == "cafe-how-to-use-x-y"
    with pytest.raises(NewVideoError):
        newvideo.slugify("???")
    with pytest.raises(NewVideoError):
        newvideo.slugify("Page")


def test_catalog_append_keeps_file_loadable_and_old_lines(env):
    call(env, *base(), "--docs", "windows")
    text = (env / "data" / "videos.yaml").read_text()
    assert text.startswith(EXISTING) and text.endswith("\n")
    assert text.splitlines()[-1] == ('- {id: "abcDEF12345", title: "Hello World: A Tip", '
                                     'topic: "Getting started", docs: "windows"}')
    vids = videos.load(env / "data" / "videos.yaml", {"windows"})
    assert [(v.id, v.title, v.docs, v.short) for v in vids][-1] == (ID, "Hello World: A Tip", "windows", False)


def test_append_adds_missing_trailing_newline_and_quotes_title(env):
    (env / "data" / "videos.yaml").write_text(EXISTING.rstrip("\n"))
    call(env, *base(), "--title", 'Say "hi": #1 {x}', "--no-post")
    vids = videos.load(env / "data" / "videos.yaml", set())
    assert [v.title for v in vids] == ["Old one", 'Say "hi": #1 {x}']


def test_shorts_url_implies_short_and_post_uses_shorts_url(env):
    call(env, f"https://www.youtube.com/shorts/{ID}", "--topic", "Getting started", "--summary", "S.")
    assert videos.load(env / "data" / "videos.yaml", set())[-1].short
    post = (env / "content/en/blog/2026-10-03-hello-world-a-tip.md").read_text()
    assert post.rstrip().endswith(f"https://www.youtube.com/shorts/{ID}")


def test_duplicate_id_refused_and_nothing_written(env):
    with pytest.raises(NewVideoError, match="already in"):
        call(env, *base("old00000001"))
    assert (env / "data" / "videos.yaml").read_text() == EXISTING
    assert not list((env / "content" / "en" / "blog").iterdir())


def test_bad_topic_refused(env):
    with pytest.raises(NewVideoError, match="unknown topic"):
        call(env, ID, "--topic", "Nope", "--summary", "S.")


def test_short_with_docs_refused_by_catalog_rules(env):
    with pytest.raises(NewVideoError, match="short"):
        call(env, *base(), "--short", "--docs", "windows")
    assert (env / "data" / "videos.yaml").read_text() == EXISTING


def test_unpublished_docs_refused(env):
    with pytest.raises(NewVideoError, match="unpublished"):
        call(env, *base(), "--docs", "missing")


def test_post_parses_with_the_blog_loader(env):
    call(env, *base(), "--category", "New features", "--tag", "x: y", "--summary", "A: tricky 'summary' #1.")
    posts = load_posts(env / "content" / "en" / "blog", env / "media")
    (post,) = posts
    assert (post.title, post.slug, post.date.isoformat()) == ("Hello World: A Tip", "hello-world-a-tip", "2026-10-03")
    assert post.summary == "A: tricky 'summary' #1."
    assert post.categories == ("New features",) and post.tags == ("x: y",)
    assert post.image is None
    assert post.body_md.strip() == f"A: tricky 'summary' #1.\n\nhttps://www.youtube.com/watch?v={ID}"
    assert "date: '2026-10-03'" in post.source.read_text()


def test_no_categories_omits_keys_and_no_post_skips_file(env):
    call(env, *base(), "--no-post")
    assert not list((env / "content" / "en" / "blog").iterdir())
    call(env, *base("zzzzzzzzzzz"))
    text = next((env / "content/en/blog").iterdir()).read_text()
    assert "categories" not in text and "tags" not in text and "image" not in text


def test_existing_post_not_overwritten(env):
    target = env / "content/en/blog/2026-10-03-my-slug.md"
    target.write_text("keep me")
    with pytest.raises(NewVideoError, match="already exists"):
        call(env, *base(), "--slug", "my-slug")
    assert target.read_text() == "keep me"
    assert (env / "data" / "videos.yaml").read_text() == EXISTING


def test_thumbnail_written(env):
    call(env, *base(), "--no-post")
    assert (env / "media" / "videos" / f"{ID}.webp").is_file()


def test_oembed_failure_names_the_id(env):
    def boom(url):
        raise OSError("offline")

    with pytest.raises(NewVideoError, match=ID):
        call(env, *base(), fetcher=boom)
    assert (env / "data" / "videos.yaml").read_text() == EXISTING


def test_explicit_title_skips_oembed(env):
    def boom(url):
        raise AssertionError("network used")

    call(env, *base(), "--title", "Given", "--no-post", fetcher=boom)
    assert videos.load(env / "data" / "videos.yaml", set())[-1].title == "Given"


def test_main_reports_errors_with_exit_code(capsys):
    assert newvideo.main(["bogus", "--topic", "Getting started", "--summary", "S."]) == 1
    assert "cannot find" in capsys.readouterr().err


def test_failed_thumbnail_leaves_catalog_untouched(tmp_path):
    import argparse
    from datetime import date

    from sitegen import newvideo

    data = tmp_path / "data"
    data.mkdir()
    (data / "videos.yaml").write_text("# header\n", encoding="utf-8")
    args = argparse.Namespace(video="n8Y8N27uFzY", topic="Getting started", summary="s", title="T", slug=None,
                              date=None, docs=None, short=False, category=[], tag=[], no_post=True)

    def boom(url):
        raise OSError("offline")

    with pytest.raises(RuntimeError):
        newvideo.run(args, opener=boom, data=data, content=tmp_path / "c", media=tmp_path / "m",
                     docs_pages=set(), today=date(2026, 1, 1))
    assert (data / "videos.yaml").read_text(encoding="utf-8") == "# header\n"
