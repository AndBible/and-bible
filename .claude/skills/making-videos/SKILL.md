---
name: making-videos
description: >
  Use when making or re-rendering an AndBible video episode: tutorial, "What's new" or a Short, with the
  narrated pipeline in website/video/. Triggers: "tee video", "uusi tutoriaalivideo", "renderöi video",
  "uusi jakso", "make a video", "new tutorial episode", "render the episode", "record a clip", "video subtitles".
---

# Making videos

The pipeline (script, one HTML page per scene, headless-Chrome frames, ffmpeg, Whisper timings) lives in
`website/video/`; `website/video/README.md` is the reference for every command. The design is
`docs/superpowers/specs/2026-11-13-andbible-video-production-design.md`; the worked example is
`website/video/episodes/poc-title-bar-search/` (script, scenes, `capture.py`, `NOTES.md`).

## Rules

- **One episode per JailBee child container.** The episode writes only into its own
  `.local/video-media/<episode>/`; `shared/music/` is common. `bash website/video/media-init.sh <episode>`
  creates the folders.
- **Exclusive resources:** the Android emulator (`android-emulator` skill, one per container), the Windows VM
  (`windows-vm` token; never next to the emulator) and the remote Mac (`ios-mac` lock). Kill the emulator when
  captures are done.
- **Real captures only.** Every picture of the interface is a real capture with seeded demo data; never invent UI.
  Name what cannot be captured honestly. Captures come from a pinned commit recorded in `NOTES.md`.
- **Storage:** scripts, scenes, tooling, `NOTES.md` in the repo; irreplaceable sources (own narration FLAC, music,
  trimmed clips, translated SRTs) in `.local/video-media/<episode>/`; caches and renders in `.local/video-cache/` and
  `.local/video-out/<episode>/`. Final MP4s are never committed.
- **Paid calls (TTS, Whisper)** only after the maintainer approved them in-session (in a child: through the
  orchestrator). `render.py tts --dry-run` first and report the character count. The key never goes into the repo.
- No narration or caption states a duration. Scene timing is a pure function of time (no wall clock).

## New episode checklist

1. **Script:** `episodes/<name>/script.yaml` and `scenes/*.html`, narration written as speech, beats anchored with
   `JB.word("phrase")` (README: "Pinning beats to words").
2. **Captures:** build `assembleStandardGithubDebug` at a commit you record; boot the emulator, install, push modules
   (`scripts/store-shots/prepare-lang.sh en`, `android-seed.sh`, `andbible-emu.sh push-module`). Script the UI
   driving in an episode `capture.py` while `scripts/store-shots/run.sh screenrecord --size 1080x2340` records, and
   trim with `storeshots.record.trim_cmd` (retakes then cost one command). Create the BSB search index once before
   recording search.
3. **Approval and narration:** `render.py tts --dry-run`, ask for approval, then `render.py tts`
   (`--voice own` uses the maintainer's recordings: `voice --script`, record, `voice --accept`, `voice --check`).
4. **Frame checks:** `render.py preview <scene> --at <t> [--format short]` after each anchor; contact sheet of every
   scene; nothing under the Short's caption band; the clip in its device frame readable.
5. **Render:** `render.py render main` and `render short` (full-bleed app by default; `--short-layout framed` for the
   phone-in-a-frame variant, README "Palette, themes and Short layouts"); watch them end to end (contact sheet of the
   final MP4s). The outro carries the on-screen sponsor line (not narrated).
6. **Maintainer review:** hand the maintainer the rendered MP4s (paths) and wait for approval. The narration text
   and the English SRT are final only then.
7. **Subtitles (only after that approval):** `update-translations` skill, section "Video subtitles" (Tier 1+2);
   every file passes `srt_validate.py`. Never translate before the render is approved: a text change after it
   makes every translation stale.
8. **NOTES.md:** pinned commit, capture provenance, production status, proposed YouTube copy for main and Short
   (README "Publishing an episode").
9. **After the maintainer uploads:** `website/data/videos.yaml`, the docs page embed and the app link
   (`video-blog-post` skill and the spec's "App links"/"Website" sections).

## Music

Music is the shared track under `.local/video-media/shared/music/`; the episode opts in with `music:` (README
"Music"). The maintainer's settings: no ducking (`duck: null`), `volume: 0.20` (speech stays about 7 dB above the
bed; louder is fine, quieter was judged too quiet).

The AndBible theme (Suno, prompted at 110 BPM, measured 109.7 BPM, 146.8 s), approved by the maintainer 2026-11-13 with loop points chosen by ear:

```yaml
music:
  file: shared/music/curious_pulse.wav
  volume: 0.20
  duck: null
  loop: {start: 11.0, end: 118.18}
```
 For a new track, loop points are chosen **once per track** with
`render.py music-points FILE`, then confirmed **by ear** with `render.py music-loop-test` (the seams) before they go
into `script.yaml`. Shorter videos play the plain looping bed.

## Traps

- A loop seam must be a whole number of bars to a few hundredths of a second, or every seam skips: 118.0 by ear was
  0.18 s short (audible); 118.18 was found by correlating the onset envelope after `start` with the one after
  candidate ends (49 bars). `music-points`' tempo estimate (110.1) and Audacity's (120) were both off; trust the
  envelope correlation, then the ear.

- A scene clip is shown by frame index from a pre-decoded PNG sequence; `screenrecord` is variable frame rate, so
  trim with `trim_cmd` (constant 30 fps) or later cuts show stray frames.
- The results/History sheets are invisible to `uiautomator`: tap them by coordinates.
- `JB.word` throws for a phrase missing from the narration (by design); a `data-start` phrase without word
  timings (preview before TTS) degrades to 0 with a warning.
