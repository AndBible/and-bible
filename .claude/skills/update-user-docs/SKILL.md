---
name: update-user-docs
description: >
  Use to change or add a user documentation page on andbible.org (website/content/en/docs). Triggers:
  "päivitä käyttöohje", "päivitä docs-sivu", "lisää dokumentaatiosivu", "docs pitää päivittää tämän
  muutoksen jälkeen", "update the user docs", "add a docs page", "document this feature",
  "add a deep link into the docs".
---

# Update user documentation

Chat in Finnish; docs are English. Never push. `website/content/en/docs/*.md` is the source of
truth: edit it directly. Never re-run `website/sitegen/migrate/` (one-shot RST conversion); the old RST
repo `AndBible/docs` is deprecated.

1. **Edit** the matching page. Facts come from the user or the code; never invent behaviour.
2. **New page.** Create `website/content/en/docs/<page>.md` and list it in the `nav` of
   `website/zensical.toml`; a page missing from the nav is not built.
3. **Headings are anchors.** The app links `DocsLinks.page("<page>", "<anchor>")`
   (`sharedCore/src/commonMain/kotlin/net/bible/sharedcore/docs/DocsLinks.kt`) to
   `https://andbible.org/docs/<page>/#<anchor>`. Do not rename a linked heading;
   `website/tests/test_app_deep_links.py` fails if you do. Rename needed? Change the Kotlin call in the
   same commit.
4. **New app deep link.** Use only a literal one-line call `DocsLinks.page("page", "anchor")` in Kotlin
   (the test scans `app/src/main`, `sharedUi/src`, `sharedCore/src/commonMain`; multi-line or computed
   calls fail it, and so do hardcoded `andbible.org/docs/` URLs). Make sure the anchor exists in the built page.
5. **Images.** In `website/content/en/docs/images/`, referenced relatively as `images/name.png`
   (see `getting_started.md`), not in the media repo.
6. **Related videos.** Set `docs: "<page-stem>"` on the entry in `website/data/videos.yaml`; it then
   shows under the page's Related videos. Do not set it on shorts.
7. **Translations.** Other languages fall back to the English file if theirs is missing; do not add
   translated copies unless asked.
8. **Verify.** `make site site-check`, then open `/docs/<page>/` in the built site (test server: see
   `website-maintenance`) and check the anchors.
9. **Release order.** App builds containing `andbible.org/docs` links must not ship before the
   docs cutover is live (`website/CUTOVER.md` section 0). Check that file for the current state; those
   steps are the user's host actions.
10. **Commit small** with the `Claude-Session:` trailer. A user-visible app change should update the
    docs in the same PR as the code.
