---
name: website-maintenance
description: >
  Umbrella for andbible.org website, blog and docs-site requests that fit no specific skill. Triggers:
  "päivitä verkkosivut", "muokkaa andbible.org:ia", "lisää video katalogiin", "muuta etusivun tekstiä",
  "lisää kieli verkkosivuille", "update the website", "edit the landing page text", "add a language
  to the site", "redirect an old URL". Start here, then hand off to the table below.
---

# Website maintenance

Chat in Finnish; content English. Sources: `website/README.md`, `website/content/README.md`,
`website/CUTOVER.md`.

**Map.** `website/content/en/` (source language): `site.yaml` (UI strings, nav, hero), `blog/`,
`pages/`, `docs/` (Zensical, nav in `website/zensical.toml`). `content/<lang>/` mirrors it.
`website/data/`: `videos.yaml`, `reviews.yaml`, `redirects.yaml`, `wp-uploads-redirects.yaml`.
`website/sitegen/` generator, `templates/`, `theme/`, `assets/` (css, js), `tests/` (pytest and
`*.mjs`). `website/media` = submodule (images, `videos/<id>.webp`). `website/_site`, `_build` = generated.

**Loop.** `make site` (build), `make site-check` (checks + pytest), `make site-serve` or
`python3 -m http.server 8000 --bind 0.0.0.0 --directory website/_site` restarted with `setsid` and
output to a file if it died. Both need `uv`.

| Request | Skill |
|---|---|
| New YouTube video / short | `video-blog-post` |
| Other blog article | `write-blog-post` |
| Thumbnail changed on YouTube | `refresh-video-thumbnails` |
| Docs page, docs anchors, app deep link | `update-user-docs` |
| Landing-page reviews | `update-website-reviews` |

**Other operations**
- Video in catalog without a post: `cd website && uv run python -m sitegen.newvideo <url> --topic "<T>" --summary "<s>" --no-post`
  (or edit `data/videos.yaml`: `id, title, topic, published` required; then `make site-thumbs`).
- UI strings / hero text: `content/en/site.yaml`. Other languages merge over English per key.
- New language: `content/<lang>/site.yaml` existing = the language exists; any missing file falls back to English.
- Old URLs (WordPress, Read the Docs) must never break: `data/redirects.yaml` (paths ending `/` = dirs;
  a site-path target must exist), guarded by `tests/test_legacy_urls.py`, `test_redirects.py`, `test_site_links.py`.
- Theme: colours only via tokens (`tests/test_home.py` fails on colour literals outside token blocks),
  check light and dark, no horizontal overflow at 360 px. No third-party requests (`sitegen.check`).
- Media: commit in `website/media` first, then the gitlink bump; tell the user to run
  `git -C website/media push origin HEAD:master` before `make push`.

**Never.** Push from the container; re-run `sitegen/migrate/` scripts; edit `website/_site` or `_build`;
do the DNS/Pages cutover (`website/CUTOVER.md` are the user's host steps).

**Tests.** `make site-check` must be green. Playwright scripts (not CI; need site on :8000; setup in `website/README.md` "Browser checks"): `screens.mjs` (overflow + screenshots), `docs-header.mjs` (header, theme
persistence), `docs-layout.mjs` (sidebar/TOC/overflow), `docs-badges.mjs` (install badges),
`hero-layout.mjs` (hero above the fold), `reviews-carousel.mjs`, `video-card-link.mjs` (YouTube link hidden with JS).
