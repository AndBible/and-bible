---
name: refresh-video-thumbnails
description: >
  Use when the user changed a video's thumbnail on YouTube and the local copy on andbible.org must be
  refreshed. Triggers: "päivitin videon thumbnailin", "päivitä thumbnailit", "refresh video thumbnail",
  "thumbnail changed on YouTube", "update the video thumbnails".
---

# Refresh video thumbnails

Chat in Finnish. Local copies live in the media submodule as `website/media/videos/<id>.webp`.
Never push.

1. **Inputs.** Need the video URL(s) or id(s). Missing? Ask with `AskUserQuestion`. Use `--all`
   only when the user asks for every video.
2. **Run** (repo root, needs network):
   `make site-thumbs THUMBS_ARGS="--refresh <id-or-url> [...]"`. Unsure which ids? Add `--dry-run`
   first. An id not in `website/data/videos.yaml` or the content is refused.
3. **Read the summary.** Each video is `changed` or `unchanged` (unchanged: YouTube still serves the
   same image, tell the user); a `FAILED` id keeps its old file and the exit code is non-zero. The
   helper prefers `maxresdefault`, falls back to `hqdefault` (black letterbox bars cropped) and
   then `mqdefault`.
4. **Verify:** `make site site-check`. If the user wants to see the result, take a screenshot of
   the card (cards show on `/videos/` and in the home teaser).
5. **Commit.** First in the media submodule:
   `git -C website/media add videos && git -C website/media commit -m "videos: refresh thumbnail <id>"`,
   then the gitlink bump in the main repo (`git add website/media`).
6. **Hand over.** Never push. Tell the user to run `git -C website/media push origin HEAD:master`
   before `make push`.
