# andbible.org

Static site generator for the AndBible website, built with Python and Jinja2.

## Build commands

- `make site` — Build the site into `website/_site/`
- `make site-check` — Run checks and tests
- `make site-serve` — Serve the built site at http://localhost:8000/

## Browser checks (Playwright, not part of CI)

The `website/tests/*.mjs` scripts drive a headless Chromium against the built site served on
http://localhost:8000/ (`make site` then `python3 -m http.server 8000 --directory website/_site`). Setup, once per
checkout (`.local/` is git-ignored and shared between host and container):

```bash
mkdir -p .local/playwright && (cd .local/playwright && npm install playwright@1.60)
npx playwright install chromium-headless-shell   # browser lands in ~/.cache/ms-playwright
```

Run a script:

```bash
export NODE_PATH=$PWD/.local/playwright/node_modules
export CHROME=$(ls ~/.cache/ms-playwright/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell | tail -1)
node website/tests/reviews-carousel.mjs    # also docs-header, docs-layout, docs-badges, hero-layout, video-card-link, screens
```

Each script prints PASS/FAIL checks and exits 1 on failure; their headers say what they guard.

## Go-live

See [CUTOVER.md](CUTOVER.md) for the cutover runbook.

## Content structure

See [content/README.md](content/README.md) for guidance on organizing content.

## Zensical version

This project uses `zensical==0.0.67`.
