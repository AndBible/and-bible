# andbible.org cutover runbook

Everything here is done by hand on the host, in order (the container never pushes). Each step ends
with a `Check:` line; do not continue until it holds. Commands run from the repo root
(`and-bible/`) unless stated otherwise.

## 0. Release order (read first)

- The Android app on `homepage` links to `https://andbible.org/docs/<page>/#<anchor>`. **Do not
  release an app build containing those links before step 4 is live** (andbible.org/docs must
  answer first).
- Older app versions in the field use `https://docs.andbible.org/en/latest/<page>.html#<anchor>`.
  They keep working through the docs redirect site (step 5; a small JS stub preserves the `#anchor`).
  **`docs.andbible.org` must stay up as the redirect site indefinitely.** Never point it elsewhere
  or let the `AndBible/docs` Pages site lapse.
- Order: steps 1-6 complete and checked, then the app release.

## 1. Push the branch and the media submodule

`AndBible/andbible-website-media` already exists (public, empty until the first push).

1. Look at the submodules: `git submodule status`. For each of `app/src/test/roborazzi`,
   `docs/superpowers` and `website/media`, a leading `+` means the checkout differs from the
   committed gitlink. For each one, check for unpushed commits, for example
   `git -C docs/superpowers log --oneline @{u}..` (if no upstream is configured, compare with
   `git -C docs/superpowers ls-remote origin`).
   Remote note: `and-bible` itself pushes over SSH (`git@github.com:AndBible/and-bible.git`), but all
   three submodules (goldens, `docs/superpowers`, `website/media`) have https `origin` URLs, and
   `make push` pushes to whatever `origin` is. If an https push asks for credentials or fails, either
   switch the push URL to SSH (YubiKey touch), e.g.
   `git -C website/media remote set-url --push origin git@github.com:AndBible/andbible-website-media.git`
   (same for the other two), or run `gh auth setup-git`. The `ls-remote` checks below work
   unauthenticated for public repos (`docs/superpowers` is private and needs auth).
2. **First push of the media repo only**, before anything else:
   `git -C website/media push origin HEAD:master`
   Without this, `make push` would push the submodule to a branch named `homepage`, which would
   become the empty repo's default branch.
3. `make push`. What it does (see the root `Makefile`): `PUSH_SUBMODULES` is
   `app/src/test/roborazzi docs/superpowers website/media`. For each one it pushes the gitlink
   commit committed in HEAD to `refs/heads/<current branch>` of the submodule's `origin` (so the
   goldens and `docs/superpowers` repos also get a `homepage` branch), then runs
   `git push --recurse-submodules=check -u origin homepage`, which refuses if any gitlink
   (`jsword` included) is unpushed. The pushes need a YubiKey touch.

Check: `git ls-remote https://github.com/AndBible/andbible-website-media.git` lists `refs/heads/master`
at the same SHA as `git -C website/media rev-parse HEAD`, and `git ls-remote origin homepage`
matches `git rev-parse HEAD`.

## 2. Merge to `current-stable`

Tip: do the Pages setting of step 3 (Source = "GitHub Actions") before merging; it is a repo setting
independent of the merge, and it avoids one red `deploy` run.

Open a PR from `homepage` to `current-stable` and merge it. The `Website` workflow
(`.github/workflows/website.yml`) runs on the PR (job `check`); on the push to `current-stable` it
also runs job `deploy`, which fails until step 3 is done.

Check: the PR's `check` job is green.

## 3. Enable GitHub Pages

In the and-bible repo, Settings, Pages: Source = "GitHub Actions"; Custom domain = `andbible.org`
(the site's own `CNAME` file also carries it). Then re-run the push run of the `Website` workflow on
`current-stable` (Actions, Website, Re-run all jobs); `deploy` only runs for a push to `current-stable`.

Check: the `deploy` job is green and `https://andbible.github.io/and-bible/` shows the site
(it may redirect to the custom domain once DNS is set).

## 4. DNS for andbible.org

At the DNS provider:

- A records: `185.199.108.153`, `185.199.109.153`, `185.199.110.153`, `185.199.111.153`
- AAAA records: `2606:50c0:8000::153`, `2606:50c0:8001::153`, `2606:50c0:8002::153`,
  `2606:50c0:8003::153`
- `www` CNAME: `andbible.github.io`

**Keep `support.andbible.org` and `shop.andbible.org` unchanged.** The module repositories and the
shop depend on them. Also check whether any other `*.andbible.org` record in use still points at
WordPress.com.

Check: `dig +short andbible.org` returns the four A addresses; `dig +short www.andbible.org`
returns `andbible.github.io.`. When GitHub has issued the certificate (Settings, Pages), tick
"Enforce HTTPS", then `curl -sI https://andbible.org/` returns 200 and `curl -sI http://andbible.org/`
redirects to https.

## 5. docs.andbible.org becomes a redirect site

GitHub Pages serves one custom domain per repo, so this lives in the `AndBible/docs` repo, not in
and-bible.

1. Generate (or refresh) the stubs. The container already produced them in
   `.local/docs-redirect-site/` (shared with the host). To regenerate:
   `cd website && uv run python -m sitegen.migrate.docs_stubs --out <checkout of AndBible/docs>`.
   **Warning:** the generator deletes everything in `--out` except `.git`. On `main` this removes the RST
   sources, which is safe only after the `rtd-final` tag (step 2 below) is pushed.
   The generator rewrites the stubs, `404.html`, `index.html`, `README.md` (points readers to
   andbible.org/docs and the monorepo) and `CNAME` (`docs.andbible.org`), and keeps the `.git`
   directory of the target.
2. In a checkout of `AndBible/docs`, first tag the last RST commit so the history stays reachable:
   `git tag rtd-final && git push origin rtd-final`.
3. Put the stubs on either a new branch `gh-pages` (`git switch --orphan gh-pages`, copy the stubs
   in, or generate straight into the checkout) or on `main` after the tag. Commit and push.
4. In AndBible/docs, Settings, Pages: deploy from that branch (root), Custom domain
   `docs.andbible.org`.
5. DNS: `docs.andbible.org` CNAME `andbible.github.io`. Tick "Enforce HTTPS" when the certificate is
   issued.
6. In the Read the Docs project admin: remove the custom domain `docs.andbible.org`, then
   deactivate the project.

Check: `curl -s https://docs.andbible.org/en/latest/ai.html` returns the stub with a redirect to
`https://andbible.org/docs/ai/`, and opening `https://docs.andbible.org/en/latest/ai.html#setting-permissions`
in a browser lands on `https://andbible.org/docs/ai/#setting-permissions`. The README on GitHub
(AndBible/docs) shows the redirect-only notice.

## 6. Spot checks

The legacy URL list: `cd website && uv run python -c "print(open('tests/fixtures/wp_urls.txt').read())"`
(158 paths). Sample about 10 on the live site; each must open the migrated page or redirect to it.
Include at least:

- `/2023/12/23/new-video-how-save-space-in-google-drive-storage-when-using-device-synchronization/`
- a `/category/...` and a `/tag/...` URL from the list
- `/feed/` (RSS)
- `/privacy.html`
- `/tutorial-videos/` (redirects to `/videos/`; it comes from `data/redirects.yaml`, not `wp_urls.txt`)
- `/printable-promotional-material/`
- `/sponsor-andbible-financially/`, and the bare no-slash form `/sponsor-andbible-financially`:
  only the directory stub exists, so the no-slash form relies on GitHub Pages' automatic redirect
  to the slash URL. Verify it.
- a few `/wp-content/uploads/...` stubs. Limitation: HTML stubs redirect direct navigation only,
  not `<img>` hotlinks from other sites.
- `/blog/page/2/`
- docs deep links: `https://docs.andbible.org/en/latest/ai.html#setting-permissions`,
  `https://docs.andbible.org/en/latest/releases/release_5_0.html`,
  `https://docs.andbible.org/en/stable/windows.html` (no stub exists for it: it is served by
  `404.html`, so GitHub Pages returns HTTP 404 and `curl -I` shows 404, but the browser lands on
  `/docs/windows/` with the hash)

Check: all open without a 404 and with the expected target (anchors preserved for the docs links).
Then do step 0's app release.

## 7. WordPress.com

Leave `andbibleorg.wordpress.com` up for a month, then close the WordPress.com plan. Comments are
not migrated (decision 2026-10-03).

Check: a calendar reminder for the closing date exists.

## Content follow-ups for you

Before or soon after go-live:

- Review the video curation in `website/data/videos.yaml` (topics; the 22 dropped videos are listed
  in the commit message: `git log --grep 'video catalog' -1`).
- The video thumbnails are YouTube `hqdefault` images, which have black bars baked in.
- Review the Q2/2026 post excerpt text and the docs landing page descriptions.
- Confirm comments are not migrated (decision 2026-10-03).
- Pages "Enforce HTTPS" on both repos (steps 4 and 5).
- The RSS feed is at `/feed/`, which GitHub Pages serves as `text/html`. Feed readers normally
  accept that; verify by subscribing in a feed reader.

## Known gaps

- The landing hero card has no "current version" line (spec section 3 lists it; there is no
  version source for it).
- Unreferenced WordPress attachments have no `/wp-content/uploads/...` stubs (only referenced ones).
- `actionlint` and the first CI run of `website.yml` have not been executed. The action versions
  follow the working jailbee repo (`checkout@v7`, `setup-uv@v7`, `upload-pages-artifact@v5`,
  `deploy-pages@v5`); verify the first run in step 2/3.
