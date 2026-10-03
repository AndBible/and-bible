---
name: update-website-reviews
description: >
  Use to curate the "What users say" review carousel on the andbible.org landing page. Triggers:
  "päivitä arvostelut", "hae uudet arvostelut", "päivitä etusivun reviewit", "update the website
  reviews", "refresh Play reviews", "curate landing page reviews".
---

# Update landing-page reviews

Chat in Finnish; data English. Never push. Rules: header of `website/data/reviews.yaml` and
"Landing page reviews" in `website/content/README.md`.

1. **Fetch candidates** (needs egress to play.google.com):
   `cd website && uv run python -m sitegen.play_reviews [--min-len 60 --max-len 420 --limit 30 --count 200]`.
   If the endpoint is blocked or fails, say so and ask the user; do not invent reviews. The tool only
   prints; names shown are for reference and must never go into the data.
2. **Select** only 5-star reviews with useful concrete content: features, speed, offline, no ads,
   free, developer responsiveness. Skip pure prayers or blessings, complaints, feature requests,
   competitor names, and unverifiable claims (security, telemetry) unless the user confirms.
3. **Wording unchanged.** Fix only obvious typos, capitalisation and punctuation; mark every cut with
   `…`; never rephrase; no reviewer names anywhere.
4. **Edit** `website/data/reviews.yaml`: entries are `year` + `text` only. Aim for 15-25 entries
   (20 today). File order is display order: interleave years and topics, short strong one first
   (the first slide shown is chosen randomly at runtime). Update the header's count and fetch date.
5. **Verify.** `make site site-check` (validated by `sitegen/reviews.py`, `tests/test_reviews.py`).
6. **Optional carousel check** with the site served on :8000 (see `website-maintenance`):
   `NODE_PATH=<dir containing node_modules/playwright> CHROME=<chrome-headless-shell> node website/tests/reviews-carousel.mjs [--out <scratch>]`.
   There is no repo doc for those two variables: here the browser is at
   `~/.cache/ms-playwright/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell`;
   find the Playwright package with `find / -type d -name playwright -path '*node_modules*' 2>/dev/null`
   (none was installed when this was written; `npx -y playwright` can fetch it). Unavailable? say the check was skipped.
7. **Commit** with the `Claude-Session:` trailer.
