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
third-party request until the visitor clicks). After adding one, run `make site-thumbs` to fetch
the thumbnail into the media repo.

## Galleries

```html
<div class="gallery" markdown>
![First screenshot](/media/blog/2025/01/one.webp)
![Second screenshot](/media/blog/2025/01/two.webp)
</div>
```

## User documentation

- Pages are in `en/docs/`. Add each new page to the `nav` in `website/zensical.toml`; pages not in
  the nav are not built.
- Keep headings stable: the app links to their anchors as
  `https://andbible.org/docs/<page>/#<id>`. Renaming a heading the app links to fails the tests.
- A user-visible app change updates the matching docs page in the same PR.

## Translations

`content/<lang>/` mirrors `content/en/`. Any file may be missing; the English file is used
instead. The keys in `<lang>/site.yaml` merge over the English ones, so translate only what you
need. A language exists once `content/<lang>/site.yaml` does.
