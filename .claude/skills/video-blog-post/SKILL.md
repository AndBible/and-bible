---
name: video-blog-post
description: >
  Use when the user published a new YouTube video or short and wants a blog post and catalog entry
  on andbible.org. Triggers: "tein uuden videon", "uusi video blogipostaus", "uusi short",
  "I published a new video", "blog post about my new video", "make a blog post about this short".
---

# Video -> blog post

Chat in Finnish; the post is English. Claude cannot watch videos, so the user supplies the facts.
Never push.

1. **Inputs.** Need the video URL and a 1-3 sentence description of what it shows. Missing? Ask
   with `AskUserQuestion`.
2. **Topic.** One of `sitegen.videos.TOPICS` (Getting started, Navigation & windows, Bookmarks &
   StudyPads, Search & study tools, Sync & backup, Customisation, Developer diaries). Add
   `--docs <page>` only when the video covers a published page in `website/content/en/docs/`
   (never for shorts). Ask only if truly ambiguous.
3. **Run the helper** (from `website/`; needs network for the title and thumbnail):
   `uv run python -m sitegen.newvideo <url> --topic "<T>" --summary "<text>" [--docs P] [--category "New features"]`.
   A `/shorts/` URL implies a short. It refuses duplicates, bad topics and existing posts.
4. **Flesh out the post.** Read two recent posts in `website/content/en/blog/` first and match the
   plain, friendly voice. Keep the summary paragraph and the bare video URL line; add 1-3 short
   paragraphs, optionally linking `/docs/<page>/`. No `image` (the thumbnail would duplicate it).
   Categories: reuse existing ones (`grep -h -A3 '^categories' website/content/en/blog/*.md`),
   typically "New features" or "tips & tricks".
5. **Verify:** `make site site-check` from the repo root.
6. **Commit.** First in the media submodule: `git -C website/media add videos && git -C website/media commit`
   (message `videos: thumbnail <id>`), then in the main repo the gitlink bump together with
   `website/data/videos.yaml` and the post. End messages with the `Claude-Session:` line. Tell the
   user to run `git -C website/media push origin HEAD:master` before `make push`
   (see `website/CUTOVER.md`).
7. Send a screenshot only if asked.
