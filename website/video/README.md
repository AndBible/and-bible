# Narrated videos

The YouTube videos (a main video and a Short per episode) are rendered here
from sources: a script, one HTML page per scene, and the captures and
screenshots those pages show. Narration is recorded or OpenAI text-to-speech.
The finished videos are not committed: they go to YouTube, and the site embeds
them from there.

Origin: the renderer is ported from the JailBee project's `website/video`
(GPL-compatible), restyled with AndBible's fonts and palette.

```bash
uv run website/video/render.py tts --dry-run        # what narration is missing, no API call
uv run website/video/render.py tts                  # generate it
uv run website/video/render.py preview hook --at 12 # one frame of a scene, as PNG
uv run website/video/render.py preview hook         # one scene as a quick, small mp4
uv run website/video/render.py render main          # main-tts.mp4 + main-tts.srt
uv run website/video/render.py render short         # short-tts.mp4 + short-tts.srt (full-bleed app)
uv run website/video/render.py render short --short-layout framed  # short-tts-framed.mp4 (phone in a device frame)
```

`--episode DIR` picks an episode directory; without it the only directory under
`episodes/` is used (several: the error names them). Output lands in
`.local/video-out/<episode>/` (`main-tts.mp4`, `main-tts.srt`, `short-tts.mp4`, `short-tts.srt`, and `-own` for the own voice);
narration clips, word timings and per-scene renders in `.local/video-cache/`.
Both are git-ignored.

## Storage

| What | Where | Why |
|---|---|---|
| Scripts, scene pages, render/capture tooling, seed definitions, `NOTES.md` | the repo, `website/video/` | small, essential, reviewable |
| **Irreplaceable sources**: narration recordings (FLAC), the music track, selected trimmed capture clips and screenshots used by scenes, translated subtitle files | `.local/video-media/` (plain shared directory, no git; the host's machine backup covers it) | need a human or an emulator session to recreate |
| TTS/Whisper cache, per-scene renders, raw capture sessions, final MP4s | `.local/video-cache/`, `.local/video-out/<episode>/` | regenerable; `.local/` is a host mount that survives container destruction; the TTS cache saves money |

Final MP4s are not committed to GitHub. YouTube is the published archive;
`.local/` the working copy. Each episode writes only into its own folder
`.local/video-media/<episode>/`; only `shared/` (the music) is common.

## What it needs

- **ffmpeg, ffprobe and Chrome.** Name the Chrome binary with
  `JAILBEE_VIDEO_CHROME` or `CHROME` (the render exits naming both if neither
  is set).
- **Playwright and PyYAML**: declared inline at the top of `render.py`, so
  `uv run` fetches them into its own cache. The project's dependencies are
  untouched.
- **An OpenAI API key** for TTS narration and word timings: `OPENAI_API_KEY`, or
  the file `~/.config/openai/api-key`. It is never written to the repo, a log or
  the cache. Attach it to the container with the optional `openai` mount
  (`jailbee mount openai <container>`); `api.openai.com` is in the egress
  allowlist. `tts --dry-run` needs neither network nor key.

## How an episode works

`episodes/<name>/script.yaml` lists the scenes in order. Each has an `id`, a
`page`, its `narration` and the `formats` it appears in (`main` 1920×1080,
`short` 1080×1920); `params` are passed to the page. Episode-level keys set the
voice (`tts.model`, `tts.voice`, `tts.instructions`), `fps`, `pad` (silence
either side of a scene's narration) and `fade` (the cross-fade between scenes).

1. **Narration.** One TTS call per scene, cached under a hash of the model,
   voice, instructions and text. Re-rendering costs nothing; editing one
   scene's narration regenerates that scene's clip only.
2. **Timing.** A scene lasts its clip plus `pad` either side. Pages pin each
   beat to the phrase it illustrates, `JB.word("green dot")` (see step 5 and
   *Pinning beats to words*), so the picture follows the voice when the text
   changes. `JB.at(fraction)` is the older way and is kept for what has no
   phrase to anchor to.
3. **Frames.** Each page is opened in headless Chrome at the format's size and
   stepped frame by frame: `JB.seek(t)` puts every Web Animation at time `t`,
   then the page is screenshotted and piped to ffmpeg. Nothing runs on
   wall-clock time, so a frame is the same however slow the machine.
4. **Assembly.** Scenes are joined with cross-fades, the clips are laid under
   them at their offsets and loudness-normalised, and an SRT is written with
   one cue per sentence.
5. **Word timings and burned-in captions.** `render.py tts` also sends each
   clip to Whisper (`whisper-1`, word timestamps) once and caches the result
   next to the clip, keyed by the same hash. The script's own words, with
   their spelling and punctuation, are aligned to those timings
   (`pipeline.align_words`). The short format gets them burned in: two or
   three words at a time, the spoken one in amber (`runtime.js`'s `captions()`,
   styled in `base.css`). A scene opts out with `params: {captions: false}`.
   The main video has none, and the SRT files are unchanged.

Write narration as speech: "six point oh", "andbible dot org",
"star dot example dot com". The page shows the literal form. The captions are
the spoken text, so fix anything YouTube's auto-captions would be better at in
YouTube Studio rather than here.

### Writing a scene

A page links `../../lib/base.css` (the site's fonts, palette and the
shared components) and `../../lib/runtime.js`, then registers its
animations:

```html
<script src="../../lib/runtime.js"></script>
<script>
  JB.scene(({ word, show, zoom, ring, params, format }) => {
    const img = zoom(document.querySelector(".shot"), 2);  // crisp 2x
    show(".head", 0.15);                                     // at 0.15 s
    show(ring(img, [446, 155, 50, 52]), word("green dot"));  // a box in the image's own pixels,
  });                                                        // shown as "green dot" is said
</script>
```

`html[data-format="short"]` selects the portrait layout. `show(el, from, to)`
fades an element in and optionally out; when a second animation targets the
same element, pass `"forwards"` as `JB.anim`'s `fill`, or its first keyframe
overrides the first animation before the second starts.

### Pinning beats to words

Pin every highlight, ring, packet and list item to the phrase being said, not
to a fraction of the clip. Fractions are guesses that go stale the moment the
narration is edited; in the 6.0 main video several were a whole sentence off.

- `JB.word("half-filled dot")` is the scene time that phrase starts;
  `JB.word("example dot com", { end: true })` is when it ends (an arrival, the
  end of a drawn line). The phrase is matched against the narration's own words,
  ignoring case and punctuation, so write it as spoken ("six point oh").
- `JB.when(x)` takes a number (a fraction, as `at`) or a phrase, for values a
  page reads from `params` or an attribute. Use `data-word="…"` for list items
  and let `params` carry phrases (`at: {green: "green dot"}`).
- Anchor on the first words of the thing being said, and on a phrase that is
  unique in the narration: the first match wins (`open search`, not
  `search`).
- Order beats as the narration orders them. A picture that appears before the
  sentence that explains it reads as a mistake.
- A phrase that is not in the narration throws, so editing a sentence fails the
  render and names the phrase instead of drifting silently. After editing,
  `render.py tts` regenerates the clip and its word timings.

### Burned-in captions in a short

The short format gets word-by-word captions automatically. Design the portrait
layout around them:

- They occupy a band at the bottom, `--caption-bottom` (15% by default), about
  130 px tall. **Keep every scene's own text clear of that band**, whatever the
  moment: check the frame when the scene's last element has appeared. A scene
  whose text sits low sets `params: {caption_bottom: 24%}` to lift the band
  above it (the hook does, to stay under the screenshot and above its title);
  one that should have none sets `captions: false`.
- They are a dark plate with the spoken word as a filled amber chip, so they
  can never be taken for the scenes' own white-and-amber text. Do not restyle
  scene text with plates or chips.
- The overlay's classes are `burned-captions` and `burned-caption`. A scene's
  own `.captions` / `.caption` rules (`details.html` has them) are a different
  thing, and a clash silently moves the overlay.
- YouTube's Shorts controls cover the bottom stretch of the screen. If a
  rendered Short shows the captions hidden behind them, raise the default.

### Making a new episode

1. Write `script.yaml` (scenes, narration as speech) and the pages, with
   phrase anchors from the start.
2. `render.py tts`: narration and word timings (needs the OpenAI mount).
3. For each scene, `render.py preview <scene> [--format short] --at <t> --scale 0.35`
   at the moment *after* each anchor and again when everything is on screen.
   Check the right element is on screen at the right word, and in a short that
   nothing sits under the caption band. A contact sheet of the frames
   (`convert a.png b.png +append`) is quick to scan.
4. `render.py render main` and `render short`, then watch both end to end
   before *Publishing an episode*.

## Honesty rules

The videos are worth making only if they show the real thing.

- Every picture of AndBible's interface is a real screenshot or capture of the
  real app, on a real device, emulator or simulator, with the seeded demo data.
  Private names, accounts and addresses are blurred or cropped, never retyped.
- A platform that cannot be captured honestly is named, not faked.
- AI illustrations, if any, are visibly illustrations and never depict a user
  interface.
- No narration or caption states a duration.
- Example names in diagrams are examples (`example.com`), not anyone's real setup.

## Publishing an episode

Every completed render handoff includes a ready-to-paste YouTube title and
English description for **both main and Short**, without waiting for a separate
request. Include documentation/setup links, the main video's chapter timestamps,
and paths to the MP4/SRT files. Keep the current copy in the episode's `NOTES.md`
under **Proposed YouTube copy**, and give the copy in the chat as well.

Calculate chapter starts from `pipeline.timeline(...)` using the **current cached
narration durations**, padding and cross-fades; round down to whole seconds and
start at `0:00`. Recalculate after narration edits and re-renders: old chapter
lists are stale even when the scene order is unchanged. Shorts need their own
description, not a copy of the main video's chapters. If the full video's URL is
not known, refer to the channel rather than inventing a link.

After `render main` and `render short`, in this order:

1. **Watch both, end to end.** Frame checks catch layout, not pacing.
2. **Thumbnails.** YouTube wants 1280×720 for the main video. Scale the
   episode's feature image to that width and pad top and bottom with its own
   background colour rather than cropping (the image is near-black, so the
   bands do not show). A Short's cover is picked in the YouTube app; a full-size
   frame of its hook is `render.py preview <hook> --format short --at <t> --scale 1`.
3. **Upload** in YouTube Studio: video language English; subtitles from
   `.local/video-out/<episode>/<format>-<voice>.srt` via *Upload file → With timing*; the chapter list in the
   description comes from the scene start times
   (`pipeline.timeline(...)` — each slot's `start`). Upload unlisted and publish
   with the release.
4. **After the upload:** add the video to `website/data/videos.yaml`, embed it in its docs page and the release
   post, and link it from the app. Use the `video-blog-post` skill and the design's "App links" and "Website"
   sections (`docs/superpowers/specs/2026-11-13-andbible-video-production-design.md`). A thumbnail frame:
   `ffmpeg -ss <t> -i .local/video-out/<episode>/main-tts.mp4 -frames:v 1 -vf scale=1280:720 …`.

Re-rendering overwrites `.local/video-media/<episode>/subs/<format>-<voice>.en.srt`; translations made earlier
no longer match it. Re-run `srt_validate.py` on them and retranslate what fails.

Each episode's own facts — its video IDs, the illustration prompts used, the
frames picked for stills — live in `episodes/<name>/NOTES.md`.


## Palette, themes and Short layouts

- **Light palette by default** (the website's `[data-md-color-scheme="default"]` tokens in `lib/base.css`). The dark
  palette is an opt-in for scenes that showcase the app's night mode: `<body class="dark">` or
  `params: {theme: dark}` (sets `html[data-theme="dark"]`, `JB.theme`).
- **Short layouts**: `--short-layout full|framed` on `render` and `preview` (default `full`; main ignores it). `full` shows
  the clip full-bleed, no device frame, fitted to the full 1920 px height (about 886 px wide, bands of background
  colour at the sides), with the burned-in captions on a semi-transparent band over the app; `framed` is the phone in
  a device frame. The page sees `html[data-layout="full"|"framed"]` and `JB.layout`. Output names: `short-tts.mp4`
  (full) and `short-tts-framed.mp4`. A kicker with class `top-label` becomes a chip in the app's status bar in `full`.
  Keep the app's key UI clear of the caption band (bottom 15%).
- **Sponsor line**: the outro of an episode carries one short on-screen line, not narrated (the PoC: "No ads. Made
  possible by sponsors · shop.andbible.org"), so the narration never needs a retake for it.

## Device frames

`base.css` provides `.device.phone` (screen 9:19.5) and `.device.tablet` (screen
4:3). Each wraps exactly one `img` or `.clip` element:

```html
<div class="device phone"><img src="shots/search.png"></div>
```

## Narration sources

Two sources, chosen with `--voice tts|own` on `tts`, `preview` and `render`
(default `tts`). The output name carries the source: `main-tts.mp4`,
`main-own.mp4`, `short-own.srt`, and so on.

- **tts**: generated by OpenAI from the script, cached by model, voice,
  instructions and text.
- **own**: the maintainer's recording, one FLAC per scene, in
  `.local/video-media/<episode>/voice/<scene_id>.flac`. It is transcoded to a
  48 kHz mono WAV in `.local/video-cache/own/`, named by the FLAC's content
  hash. Whisper word timings are cached per clip hash for both sources.

Recording workflow:

1. `uv run website/video/render.py voice --script` writes
   `voice/script.txt`: every scene id with its narration, to read aloud.
2. Record each scene and save it as `<scene_id>.flac` next to `script.txt`.
3. `voice --accept SCENE...` stamps the recordings as read from the current
   narration (writes `<scene_id>.txt` holding that text).
4. `voice --check` prints `ok`, `missing` or `stale` per scene and exits 1 if
   any is not `ok`. A scene turns `stale` when its narration is edited after
   the stamp; re-read it and accept again.

`--voice own` with any missing or stale scene exits naming every such scene
and its status. It never falls back to TTS. Re-recording one scene changes only
that FLAC's hash, so only that scene is transcoded and re-transcribed; the
other scenes' clips, timings and rendered parts are reused.

## Music

One shared track under all videos, made by the maintainer (e.g. with Suno) and kept at
`.local/video-media/shared/music/`. An episode opts in with an optional key in `script.yaml`:

```yaml
music:
  file: shared/music/theme.flac   # relative to .local/video-media
  volume: 0.25                    # linear gain of the bed
  duck: 0.35                      # sidechain threshold x 0.1; smaller ducks on quieter speech
```

`music: null` or no key means no music. The track loops to the video's length, is ducked under
the narration, fades in over 1.0 s and out over the last 1.5 s, and is mixed with the speech
before the final loudness normalisation. A missing file makes `render` exit naming its path,
before anything is rendered.

## Clips

A scene shows a real app recording inside a `.device` frame:

```html
<div class="device phone"><img class="clip" data-clip="captures/search-open.mp4" data-start="search open"></div>
```

- `data-clip` is relative to `.local/video-media/<episode>/` (the episode directory's name).
- `data-start` is a narration phrase (resolved with `JB.word`) or a number of seconds of scene time.
  Before it the first frame is held; after the clip ends the last frame is held.
- No `<video>` and no wall-clock playback. `render.py` decodes each clip once into PNG frames at the
  episode fps (`ffmpeg -vf fps=<fps>`) in `.local/video-cache/frames/<sha256 of the file>/<fps>fps/%05d.png`
  and skips it when that directory is marked complete (an interrupted extraction is redone). It hands the
  frame directory, count and fps to the page in the `clips` URL parameter; `JB.seek(t)` then sets each
  `.clip`'s `src` to frame `clip_frame_index(t, start, fps, frames)` and waits for it to decode, so
  the same `t` always gives the same pixels.
- Check one: `uv run website/video/render.py preview SCENE --at 3` (needs no narration audio).
  Frames are cached by content hash, so re-recording a clip re-decodes and re-renders its scene.

## Subtitles

Every `render` writes the English SRT twice: `.local/video-out/<episode>/<format>-<voice>.srt` and
`.local/video-media/<episode>/subs/<format>-<voice>.en.srt` (the media copy is kept, together with the
translations). Translations are made only after the maintainer has reviewed and approved the rendered
video, when the narration is final. Translated subtitles are made with the `update-translations` skill's "Video subtitles"
section (`.claude/skills/update-translations/SKILL.md`): prepare, translate cue texts, merge, validate;
the files land next to the English one as `<format>-<voice>.<LANG>.srt` and are uploaded by hand in
YouTube Studio.
