# Proof-of-concept episode: search from the title bar

First episode made with `website/video/`. It exercises the whole pipeline: seeded real capture, real clips,
TTS narration, subtitles and their translations. It can double as the first Short.

## Production status (2026-11-13)

| Item | State |
| --- | --- |
| Script (5 scenes, 177 words) | done, `script.yaml` |
| Narration | TTS, model `gpt-4o-mini-tts-2025-12-15`, voice `marin`; 5 clips, 952 characters, about 64 s of audio; Whisper (`whisper-1`) once per clip for word timings. Paid calls pre-approved by the maintainer through the orchestrator. |
| Own narration | not recorded. `render.py voice --script` writes `.local/video-media/poc-title-bar-search/voice/script.txt`; `voice --check` reports all 5 scenes `missing` (exit 1), as intended. |
| Music | **none**: `.local/video-media/shared/music/` has no track yet, so both videos are rendered without `music:`. Add the key to `script.yaml` once the maintainer's track exists. |
| Main | `.local/video-out/poc-title-bar-search/main-tts.mp4` (1920x1080, 68.2 s) + `main-tts.srt`; light palette |
| Short | `.local/video-out/poc-title-bar-search/short-tts.mp4` (1080x1920, 68.2 s, full-bleed app, burned-in captions on a semi-transparent band) + `short-tts.srt`; framed variant `short-tts-framed.mp4` + `.srt` (phone in a device frame). It is as long as the main video because every scene is in both formats; cut it down before publishing it as a Short. |
| Subtitles | English SRT in `.local/video-media/poc-title-bar-search/subs/{main,short}-tts.en.srt`; Tier 1 translations `main-tts.<lang>.srt` (30 languages) and identical-timing copies for `short-tts`, every file validated with `srt_validate.py`. Upload by hand in YouTube Studio. |
| Watched | frame checks only: contact sheets of both final MP4s (one frame per 3 s) and per-scene previews at every anchor; the caption band is clear in the Short; audio mean -16.5 dB, peak -1.4 dB. **Still to do by a human: watch both end to end for pacing** (nobody has played the videos). |

Not done: music, own narration, publishing (no upload from containers), thumbnails.

## Capture provenance

| What | Value |
| --- | --- |
| Pinned capture commit | `22bbf75fbb` (branch `feat/video-pipeline`; after it only docs, website files and the `scripts/store-shots` screenrecord helper changed, no app code) |
| Build | `./gradlew assembleStandardGithubDebug`, version string `6.0.1271-beta#22bbf75fbb github standard debug`. It is a **debug build**: the navigation drawer header reads "Bible Study (AndBible) *Debug*" and its footer shows the debug version line. Retake with a release-like build before a public video if that matters. |
| Device | container emulator 37.3.4, AVD `screenshot_phone` (API 36, pixel_6_pro, 1440x3120), light theme, English locale, SystemUI demo mode (clock 9:41, full battery) |
| Data | demo backup `.local/store-shots/en/demo.abdb.zip` generated from `store-screenshots/demo-content/en/content.yaml` (`scripts/store-shots/prepare-lang.sh en`), seeded with `scripts/store-shots/android-seed.sh`; Sword modules from `demo-content/modules.yaml` pushed with `scripts/andbible-emu.sh push-module`. Berean Standard Bible is the active Bible; its search index was created once in the app before recording. |
| Recording | `capture.py` in this folder: reseeds, launches, drives the app through burger menu, Find, typing `light`, Enter, tapping the first result, the back arrow and system Back, while `scripts/store-shots/run.sh screenrecord --size 1080x2340` records. Raw session: `.local/video-cache/raw-captures/poc-title-bar-search/search-session.mp4` (+ `marks.json` with the step times). |
| Clips (in `.local/video-media/poc-title-bar-search/captures/`) | `search-open.mp4` (menu, Find, typing), `search-results.mp4` (the results sheet), `result-and-back.mp4` (result, back arrow, Back). Trimmed from the raw session by `trim_cmd` (constant 30 fps, no audio, source resolution 1080x2340). |

Honesty notes: every picture of the interface is a real frame from that recording. The touch dots of Android's
"show touches" are not captured by `screenrecord`, so taps are not visible. The brief spinner after opening a
result and after Back is the app's real loading state. In `open-search` the page holds the clip on its
(real, blinking-cursor) frames for about 1.5 s while the narration catches up with the typing: only the
timing of real frames is changed, nothing is added to the picture. No multi-platform shots, no sync scene in this episode.

## Retake

Boot the emulator, install the APK, push the modules, then run
`uv run --no-project --with pyyaml python website/video/episodes/poc-title-bar-search/capture.py`.
If `search-open.mp4` changes, re-check the rewind constants in `scenes/open-search.html`.

## Proposed YouTube copy

Chapters (scene starts from the cached narration durations, padding and cross-fades, rounded down).

### Main

Title: **AndBible 6.0: search from the title bar and keep your place**

Description:

> In AndBible 6.0, search starts right from the reading view. Open the menu, choose Find, type a word, and the
> results open in a sheet on top of the Bible text, so the chapter you were reading stays right where it is. Tap a
> result to read it, then go Back to return to your place.
>
> 0:00 Search without losing your place
> 0:13 Open the menu and choose Find
> 0:28 Results in a sheet over your text
> 0:41 Open a result and come back
> 0:54 More help
>
> User documentation: https://andbible.org/docs/search/
> AndBible is free and open source: https://andbible.org
>
> Subtitles are available in many languages.

### Short

Title: **Search the Bible without losing your place #AndBible**

Description:

> Search from the title bar in AndBible 6.0: results open in a sheet over the text, and Back brings you straight
> back to where you were reading. Free and open source. More at https://andbible.org/docs/search/
>
> #AndBible #BibleStudy #Shorts
