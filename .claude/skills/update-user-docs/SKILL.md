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
3. **Headings are anchors.** App deep-link targets are declared once in the `DocsLink` enum catalog
   (`sharedCore/src/commonMain/kotlin/net/bible/sharedcore/docs/DocsLink.kt`), with each entry's page
   literal and optional anchor. Call sites reuse the catalog entry, for example
   `DocsLink.AI_SETTING_PERMISSIONS.url`, which builds the URL
   `https://andbible.org/docs/<page>/#<anchor>` (without the fragment when no anchor is set).
   Do not rename a linked heading; `website/tests/test_app_deep_links.py` checks catalog targets
   against the built docs. If a linked target must change, update its catalog entry.
4. **New app deep link.** Add a `DocsLink` enum entry with the new page literal and optional anchor,
   then use that entry at call sites through `DocsLink.<ENTRY>.url`. Do not repeat page/anchor
   literals at use sites or hardcode `andbible.org/docs/` URLs. The test reads the catalog and rejects
   bypasses across `app`, `sharedCore`, `sharedLogic`, `sharedUi`, and `desktopApp`. Make sure the
   anchor exists in the built page.
5. **Images.** In `website/content/en/docs/images/`, referenced relatively as `images/name.png`
   (see `getting_started.md`), not in the media repo.
6. **Related videos.** Set `docs: "<page-stem>"` on the entry in `website/data/videos.yaml`; it then
   shows under the page's Related videos. Do not set it on shorts.
7. **Translations.** Other languages fall back to the English file if theirs is missing; do not add
   translated copies unless asked.
   After a nav/H1/episode-title change, run `cd website && uv run python -m sitegen.app_toc`, then use `update-translations` for changed `user_guide_*`/`tutorial_title_*` keys; after a rename, remove orphaned keys from `values-*` files.
8. **Verify.** `make site site-check`, then open `/docs/<page>/` in the built site (test server: see
   `website-maintenance`) and check the anchors.
9. **Release order.** App builds containing `andbible.org/docs` links must not ship before the
   docs cutover is live (`website/CUTOVER.md` section 0). Check that file for the current state; those
   steps are the user's host actions.
10. **Commit small** with the `Claude-Session:` trailer. A user-visible app change should update the
    docs in the same PR as the code.
