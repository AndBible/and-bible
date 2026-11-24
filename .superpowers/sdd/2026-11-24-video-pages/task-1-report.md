# Task 1 Report: Video Catalog Fields and Feed/Archive Split

## Status

Implemented Task 1 only. No app files or `website/video/` files were changed.

## Files changed

- `website/sitegen/videos.py`: added `KINDS`, the 2026-11-01 `FEED_SINCE`, appended `kind` and `post` to `Video`, validated both in `load`, and added `feed` / `archive` selectors.
- `website/data/videos.yaml`: documented `kind` and `post`; added the What's new 6.0 entry with the exact required values.
- `website/sitegen/build.py`: supplied loaded English blog-post slugs to the video catalog loader.
- `website/tests/test_videos.py`: added catalog validation, feed/archive cutoff and real-catalog tests.

## RED evidence

Command:

```text
cd website && uv run pytest -q tests/test_videos.py -k 'kind or post or feed or whats_new'
```

Result before implementation: `4 failed, 1 passed, 33 deselected`. Failures reported the missing `feed`/`archive` imports, unsupported `posts` keyword, and an invalid 12-character ID in the new kind test fixture. The fixture ID was corrected (`defaultVID1`) before the implementation was finalized; the initial kind-test failure was thus a fixture error, not evidence of missing kind behavior. The remaining failures correctly exposed the absent interfaces.

## GREEN evidence

Command:

```text
cd website && uv run python -m sitegen.build && uv run pytest -q tests/test_videos.py
```

Output:

```text
Build started
No issues found
Build finished in 0.82s
......................................                                   [100%]
38 passed in 0.41s
```

Also ran `git diff --check` successfully.

## Concerns

- An exploratory `uv run python -m sitegen.build --no-docs` failed because the repository's existing redirect `/docs/desktop-beta/` targets a page not built with `--no-docs`. The normal site build completed successfully; no code change was made for this unrelated behavior.
- The focused website test file is green. No broader website suite or long final gates were run, per task scope.
