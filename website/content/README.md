# Authoring guide for andbible.org

Build and preview with `make site site-serve` (http://localhost:8000/). Validate with
`make site site-check`. Both need `uv`.

## Directory map

```
website/content/
  en/                 English, the source of truth (served at the site root)
    site.yaml         UI strings, nav labels, hero text
    blog/             posts: YYYY-MM-DD-slug.md
    pages/            standalone pages: privacy.md, terms.md, ...
    docs/             user documentation (built by Zensical, served at /docs/)
  <lang>/             translation, mirrors en/ (served at /<lang>/)
```

Images and video thumbnails live in the media repo (`website/media`, a submodule), not here.

## Blog posts

File name `YYYY-MM-DD-slug.md`, with YAML front matter:

| Key | Required | Notes |
|---|---|---|
| `title` | yes | |
| `date` | yes | `YYYY-MM-DD`, must equal the file name prefix |
| `slug` | yes | lowercase-kebab-case, not `page` |
| `summary` | yes | used in lists, feeds and meta tags |
| `url_date` | no | `YYYY-MM-DD`; only for migrated posts whose old WordPress URL date differs from `date`. Leave it out for new posts: the URL is `/<url_date as YYYY/MM/DD>/<slug>/` and defaults to `date` |
| `categories`, `tags` | no | lists of names |
| `image` | no | path inside the media repo, must exist; the feature graphic |
| `image_alt` | with `image` | required whenever `image` is set, forbidden without it |

## Images

Put them in the media repo under `blog/YYYY/MM/`, as WebP. Feature graphics are 1200x630; inline
images at most 1600 px wide. Reference them as `/media/blog/YYYY/MM/name.webp`. Commit in the
media repo first, then bump the gitlink here.

## YouTube

Put the video URL alone on its own line. The build turns it into a thumbnail button (no embed, no
third-party request until the visitor clicks). After adding one, run `make site-thumbs` to fetch the
thumbnail into the media repo.

## New video -> blog post

```bash
cd website && uv run python -m sitegen.newvideo <url-or-id> --topic "Getting started" \
  --summary "One or two sentences." [--title T] [--slug S] [--date YYYY-MM-DD] [--docs PAGE] \
  [--short] [--category C] [--tag T] [--no-post]
```

It reads the title from YouTube (unless `--title`), appends a line to `data/videos.yaml`, fetches
the thumbnail into the media repo and writes `content/en/blog/<date>-<slug>.md` (no feature image:
the thumbnail would duplicate it). It refuses a known id, an unknown topic, a `--docs` page that is
not published, `--docs` on a short, and an existing post. Then extend the post body, commit in the
media repo first, bump the gitlink, and run `make site site-check`. The `video-blog-post` skill
(`.claude/skills/`) drives this end to end.

## Galleries

```html
<div class="gallery" markdown>
![First screenshot](/media/blog/2025/01/one.webp)
![Second screenshot](/media/blog/2025/01/two.webp)
</div>
```

## User documentation

- Pages are in `en/docs/`. Edit the Markdown there directly: it is the source of truth. The
  scripts in `website/sitegen/migrate/` were a one-shot RST-to-Markdown migration kept for
  reference; do not re-run them. The old RST repository (`AndBible/docs`) is deprecated.
- Add each new page to the `nav` in `website/zensical.toml`; pages not in the nav are not built.
- Keep headings stable: the app links to their anchors as
  `https://andbible.org/docs/<page>/#<id>`. Renaming a heading the app links to fails the tests.
- A user-visible app change updates the matching docs page in the same PR.

## Translations

`content/<lang>/` mirrors `content/en/`. Any file may be missing; the English file is used
instead. The keys in `<lang>/site.yaml` merge over the English ones, so translate only what you
need. A language exists once `content/<lang>/site.yaml` does.
