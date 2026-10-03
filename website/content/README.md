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
  [--published YYYY-MM-DD] [--version X.Y] [--short] [--category C] [--tag T] [--no-post]
```

It reads the title from YouTube (unless `--title`) and the publication date from the watch page
(unless `--published`; if the scrape fails the tool says so and asks for the flag), looks up the AndBible
version current on that date in the GitHub releases (`version:`, shown on /videos/ only; `--version` overrides, a failed lookup
only prints a note and leaves it out), appends a line to `data/videos.yaml`, fetches
the thumbnail into the media repo and writes `content/en/blog/<date>-<slug>.md` (no feature image:
the thumbnail would duplicate it). It refuses a known id, an unknown topic, a `--docs` page that is
not published, `--docs` on a short, and an existing post. `published` is a required catalog field
(`YYYY-MM-DD`, not in the future); the landing page teases the 3 newest videos by it. Then extend the post body, commit in the
media repo first, bump the gitlink, and run `make site site-check`. The `video-blog-post` skill
(`.claude/skills/`) drives this end to end.

## Landing page reviews

The "What users say" carousel on the home page comes from `data/reviews.yaml`, a hand-curated list
of `{year, text}` entries (validated by `sitegen/reviews.py`; the English strings are in `site.yaml`).

- Rules: only 5-star Google Play reviews, text verbatim (typos included), **no reviewer names**
  anywhere in the data or the built site, and any cut marked with `…`. Never reword.
- To refresh: `cd website && uv run python -m sitegen.play_reviews [--min-len 60 --max-len 420 --limit 30]`
  lists the most helpful 5-star reviews on the console (names shown there are for your reference
  only). Copy the year and text of the ones you pick into `data/reviews.yaml`; the tool never writes
  the catalog and the build never calls it (it uses an undocumented Google Play endpoint and fails
  with a clear message if that changes).
- It is a one-at-a-time carousel (`assets/js/reviews-carousel.js`): auto-advance every 10 s, pausing on
  hover, keyboard focus and a hidden tab, and stopping for good after prev/next; none under reduced
  motion. Without JS the cards are a plain list. **Display order = file order**, so interleave years
  and topics and put a short, strong one first. Aim for about 15-25 entries (20 today).
- After touching it run `node tests/reviews-carousel.mjs` (see its header; needs the site served).

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
