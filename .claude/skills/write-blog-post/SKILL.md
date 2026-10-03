---
name: write-blog-post
description: >
  Use for a new andbible.org blog article that is not just a video post: feature announcement, release
  notes, project news, tips. Triggers: "kirjoita blogipostaus", "tee blogiartikkeli", "uusi blogipostaus
  ominaisuudesta", "julkaisutiedote verkkosivuille", "write a blog post", "announce this feature on the blog",
  "release notes post". For a post about a new YouTube video use `video-blog-post`.
---

# Write a blog post

Chat in Finnish; the post is English. Never push. Sources of truth: `website/content/README.md`.
The quarterly "financial-report" posts come from the user's own script: never write or invent their
figures, only touch them if the user hands over the generated text.

1. **Facts.** Get topic, what changed, version numbers, dates and links from the user. Never
   invent facts, dates, numbers or versions; missing? Ask with `AskUserQuestion`.
2. **Voice.** Read two recent posts in `website/content/en/blog/` (for example
   `2026-07-04-new-feature-document-sync.md`) and match the plain, friendly voice.
3. **File.** `website/content/en/blog/YYYY-MM-DD-slug.md`, date = today unless told, slug
   lowercase-kebab-case (not `page`). Front matter: `title`, `date` (equals the file prefix),
   `slug`, `summary` (required; used in lists, feeds, meta tags). Optional `categories`, `tags`:
   reuse existing ones (`grep -h -A3 '^categories' website/content/en/blog/*.md`; in use: "New features",
   "tips & tricks", "Developer diaries", "roadmap", "Sponsoring AndBible"). No `url_date` for new posts.
4. **Images.** WebP only, in the media submodule under `website/media/blog/YYYY/MM/`. Feature
   graphic 1200x630 -> front matter `image: blog/YYYY/MM/name.webp` plus `image_alt` (both or
   neither; the path must exist). Inline images at most 1600 px wide, referenced as
   `/media/blog/YYYY/MM/name.webp`. Several screenshots: `<div class="gallery" markdown>` block.
5. **Video.** A YouTube URL goes alone on its own line (becomes a thumbnail button); then run
   `make site-thumbs`. A post about a new video: use `video-blog-post` instead.
6. **Links.** Internal posts as `/YYYY/MM/DD/slug/`, docs as `/docs/<page>/`.
7. **Docs.** The post announces a user-visible app change? Check the matching docs page needs the
   same update (`update-user-docs`).
8. **Verify.** `make site site-check` from the repo root. View the built page through the test
   server (see `website-maintenance`; `python3 -m http.server 8000 --bind 0.0.0.0 --directory website/_site`,
   restart detached with `setsid` if it is gone). A Playwright screenshot (`website/tests/screens.mjs`)
   is optional.
9. **Commit small.** Media first: `git -C website/media add blog && git -C website/media commit`,
   then the gitlink bump and the post in the main repo. End messages with the `Claude-Session:` line.
10. **Hand over.** Tell the user to run `git -C website/media push origin HEAD:master` before
    `make push`.
