# /// script
# requires-python = ">=3.12"
# dependencies = ["playwright==1.63.0", "pyyaml>=6.0"]
# ///
# Ported from the JailBee project (website/video), which is GPL-compatible with AndBible.
"""Render a narrated video episode: TTS clips, HTML scenes, ffmpeg.

    uv run website/video/render.py tts     [--episode DIR] [--dry-run] [--voice tts|own]
    uv run website/video/render.py preview SCENE [--format main|short] [--at SECONDS] [--voice tts|own]
    uv run website/video/render.py render  main|short [--episode DIR] [--voice tts|own]
    uv run website/video/render.py voice   --script | --check | --accept SCENE...

See website/video/README.md. The pure parts live in pipeline.py.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import multiprocessing
import os
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from collections.abc import Mapping, Sequence
from pathlib import Path
from urllib.parse import urlencode

sys.path.insert(0, str(Path(__file__).resolve().parent))

from pipeline import (
    out_dir,
    Episode,
    VoiceSource,
    MissingKeyError,
    Scene,
    Slot,
    Word,
    align_words,
    scene_clip_paths,
    api_key,
    cache_key,
    captions,
    english_subs_path,
    group_words,
    load_episode,
    music_filter,
    output_name,
    own_clip_key,
    own_voice_path,
    own_voice_stamp,
    voice_problems,
    voice_script,
    voice_status,
    timeline,
    total_length,
)

HERE = Path(__file__).resolve().parent
# Under the repo's .local/ so paid narration can outlive the container: there
# .local/ is commonly a host mount, while the rest of the clone dies with it.
REPO = HERE.parents[1]
CACHE_DIR = REPO / ".local" / "video-cache"
OUT_DIR = REPO / ".local" / "video-out"
MEDIA_DIR = REPO / ".local" / "video-media"
CACHE = CACHE_DIR
EPISODES = HERE / "episodes"
VIEWPORTS = {"main": (1920, 1080), "short": (1080, 1920)}
SPEECH_URL = "https://api.openai.com/v1/audio/speech"
TRANSCRIBE_URL = "https://api.openai.com/v1/audio/transcriptions"
ESTIMATED_CHARS_PER_SECOND = 15.0
"""Only for previewing a scene whose clip has not been generated yet."""


def chrome() -> str:
    path = os.environ.get("JAILBEE_VIDEO_CHROME") or os.environ.get("CHROME")
    if not path:
        sys.exit("render: no Chrome: set JAILBEE_VIDEO_CHROME or CHROME to a Chrome binary")
    return path


def default_episode(episodes: Path = EPISODES) -> Path:
    """The only episode directory, or exit naming the choices."""
    found = sorted(p for p in episodes.glob("*/script.yaml")) if episodes.is_dir() else []
    if len(found) == 1:
        return found[0].parent
    if not found:
        sys.exit(f"render: no episodes under {episodes}")
    names = ", ".join(p.parent.name for p in found)
    sys.exit(f"render: several episodes ({names}); pick one with --episode DIR")


def clip_path(episode: Episode, scene: Scene, voice: VoiceSource = "tts") -> Path:
    """The scene's narration as a WAV: the TTS cache entry, or (own) a 48 kHz
    mono transcode of the recording, named by the FLAC's bytes so a re-recorded
    scene invalidates only itself. The path need not exist yet."""
    if voice == "tts":
        return CACHE / "tts" / f"{cache_key(episode.tts, scene.narration)}.wav"
    flac = own_voice_path(MEDIA_DIR, episode.directory, scene.id)
    if not flac.is_file():
        sys.exit(f"render: no recording for {scene.id}: {flac}")
    return CACHE / "own" / f"{own_clip_key(flac.read_bytes())}.wav"


def music_path(episode: Episode) -> Path:
    """The music bed's file under the media directory; exits when it is missing."""
    assert episode.music is not None
    path = MEDIA_DIR / episode.music.file
    if not path.is_file():
        sys.exit(f"render: music file not found: {path}")
    return path


def words_path(episode: Episode, scene: Scene, voice: VoiceSource = "tts") -> Path:
    """Word timings sit under the clip's own hash: new audio, new timings."""
    return CACHE / "words" / f"{clip_path(episode, scene, voice).stem}.json"


def require_own_voice(episode: Episode, scenes: Sequence[Scene]) -> None:
    """Exit naming every scene whose recording is missing or stale; never fall
    back to TTS."""
    status = voice_status(MEDIA_DIR, episode.directory, scenes)
    bad = voice_problems(status)
    if bad:
        sys.exit(f"render: --voice own, but these scenes have no usable recording: {bad}")


def transcode_own(episode: Episode, scene: Scene) -> Path:
    """Make sure the scene's own-voice WAV exists in the cache."""
    target = clip_path(episode, scene, "own")
    if not target.is_file():
        source = own_voice_path(MEDIA_DIR, episode.directory, scene.id)
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = target.with_suffix(".part.wav")
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", source, "-ar", "48000", "-ac", "1", partial],
            check=True,
        )
        partial.replace(target)
    return target


def transcribe(scene: Scene, clip: Path, key: str) -> list[Word]:
    """Whisper's word timestamps for one narration clip."""
    boundary = hashlib.sha256(clip.read_bytes()[:4096]).hexdigest()[:24]
    fields = {
        "model": "whisper-1",
        "response_format": "verbose_json",
        "timestamp_granularities[]": "word",
        "language": "en",
    }
    body = b""
    for name, value in fields.items():
        body += (
            f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'
        ).encode()
    body += (
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="clip.wav"\r\n'
        "Content-Type: audio/wav\r\n\r\n"
    ).encode()
    body += clip.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    request = urllib.request.Request(
        TRANSCRIBE_URL,
        data=body,
        headers={
            "Authorization": f"Bearer {key}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")[:500]
        sys.exit(f"render: transcribing {scene.id} failed: HTTP {error.code}: {detail}")
    return [
        Word(str(w["word"]).strip(), float(w["start"]), float(w["end"])) for w in payload["words"]
    ]


def ensure_words(episode: Episode, scenes: Sequence[Scene], voice: VoiceSource) -> None:
    """Transcribe every clip that has no cached word timings yet."""
    missing = [
        s
        for s in scenes
        if not words_path(episode, s, voice).is_file() and clip_path(episode, s, voice).is_file()
    ]
    print(f"words: {len(scenes) - len(missing)} cached, {len(missing)} to transcribe")
    if not missing:
        return
    try:
        key = api_key()
    except MissingKeyError as error:
        sys.exit(f"render: {error}")
    for scene in missing:
        print(f"words: {scene.id}")
        words = transcribe(scene, clip_path(episode, scene, voice), key)
        target = words_path(episode, scene, voice)
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = target.with_suffix(".part")
        partial.write_text(json.dumps([[w.text, w.start, w.end] for w in words]))
        partial.replace(target)


def load_words(episode: Episode, scene: Scene, voice: VoiceSource = "tts") -> list[Word]:
    """Cached word timings, or none (a preview before `render.py tts`)."""
    if voice == "own" and not clip_path(episode, scene, voice).is_file():
        return []
    path = words_path(episode, scene, voice)
    if not path.is_file():
        return []
    heard = [Word(text, start, end) for text, start, end in json.loads(path.read_text())]
    return align_words(scene.narration, heard)


def probe_duration(path: Path) -> float:
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
        check=True,
        capture_output=True,
        text=True,
    )
    return float(result.stdout.strip())


def synthesize(episode: Episode, scene: Scene, key: str, target: Path) -> None:
    body = json.dumps(
        {
            "model": episode.tts.model,
            "voice": episode.tts.voice,
            "instructions": episode.tts.instructions,
            "input": scene.narration,
            "response_format": "wav",
        }
    ).encode()
    request = urllib.request.Request(
        SPEECH_URL,
        data=body,
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            audio = response.read()
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")[:500]
        sys.exit(f"render: TTS for {scene.id} failed: HTTP {error.code}: {detail}")
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_suffix(".part")
    partial.write_bytes(audio)
    partial.replace(target)


def ensure_clips(
    episode: Episode, scenes: Sequence[Scene], *, dry_run: bool, voice: VoiceSource = "tts"
) -> None:
    if voice == "own":
        require_own_voice(episode, scenes)
        missing = [s for s in scenes if not clip_path(episode, s, voice).is_file()]
        print(f"own voice: {len(scenes) - len(missing)} cached, {len(missing)} to transcode")
        if not dry_run:
            for scene in missing:
                print(f"own voice: {scene.id}")
                transcode_own(episode, scene)
        return
    missing = [scene for scene in scenes if not clip_path(episode, scene).is_file()]
    chars = sum(len(scene.narration) for scene in missing)
    print(f"tts: {len(scenes) - len(missing)} cached, {len(missing)} missing ({chars} characters)")
    if dry_run or not missing:
        return
    try:
        key = api_key()
    except MissingKeyError as error:
        sys.exit(f"render: {error}")
    for scene in missing:
        print(f"tts: {scene.id}")
        synthesize(episode, scene, key, clip_path(episode, scene))


def durations(
    episode: Episode,
    scenes: Sequence[Scene],
    *,
    estimate: bool = False,
    voice: VoiceSource = "tts",
) -> dict[str, float]:
    result = {}
    for scene in scenes:
        path = clip_path(episode, scene, voice)
        if path.is_file():
            result[scene.id] = probe_duration(path)
        elif estimate:
            result[scene.id] = len(scene.narration) / ESTIMATED_CHARS_PER_SECOND
        else:
            raise SystemExit(f"render: no clip for {scene.id}; run `render.py tts --voice {voice}` first")
    return result


def clip_frames(episode: Episode, relative: str, fps: int) -> tuple[Path, int]:
    """Decode a clip (path relative to MEDIA_DIR/<episode>/) into CACHE_DIR/frames/<sha256 of
    the file>/%05d.png at `fps`, once. Returns the directory and its frame count."""
    source = MEDIA_DIR / episode.directory.name / relative
    if not source.is_file():
        sys.exit(f"render: clip {relative} not found at {source}")
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    target = CACHE / "frames" / digest / f"{fps}fps"
    done = target / ".complete"
    if not done.is_file():
        shutil.rmtree(target, ignore_errors=True)  # a partial extraction is not reusable
        target.mkdir(parents=True)
        print(f"clips: decoding {relative} at {fps} fps")
        subprocess.run(
            ["ffmpeg", "-v", "error", "-i", str(source), "-vf", f"fps={fps}", str(target / "%05d.png")],
            check=True,
        )
        if not any(target.glob("*.png")):
            sys.exit(f"render: ffmpeg produced no frames from {source}")
        done.write_text("")
    return target, sum(1 for _ in target.glob("*.png"))


def scene_clips(episode: Episode, scene: Scene) -> dict[str, list]:
    """The page's clips as {path: [frame directory URL, frame count, fps]} for the scene URL."""
    result: dict[str, list] = {}
    for relative in scene_clip_paths(scene.page):
        directory, count = clip_frames(episode, relative, episode.fps)
        result[relative] = [directory.as_uri(), count, episode.fps]
    return result


def scene_url(
    slot: Slot,
    clip: float,
    fmt: str,
    words: Sequence[Word] = (),
    clips: Mapping[str, list] | None = None,
) -> str:
    query = urlencode(
        {
            "clips": json.dumps(clips or {}),
            "duration": f"{slot.length:.3f}",
            "speech": f"{slot.speech_at - slot.start:.3f}",
            "clip": f"{clip:.3f}",
            "format": fmt,
            "params": json.dumps(slot.scene.params),
            "words": json.dumps([[w.text, round(w.start, 3), round(w.end, 3)] for w in words]),
            "groups": json.dumps(
                [
                    [[w.text, round(w.start, 3), round(w.end, 3)] for w in g]
                    for g in group_words(words)
                ]
            ),
        }
    )
    return f"{slot.scene.page.resolve().as_uri()}?{query}"


def open_scene(
    browser,
    slot: Slot,
    clip: float,
    fmt: str,
    scale: float,
    words: Sequence[Word] = (),
    clips: Mapping[str, list] | None = None,
):
    width, height = VIEWPORTS[fmt]
    page = browser.new_page(viewport={"width": width, "height": height}, device_scale_factor=scale)
    errors: list[str] = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.on(
        "console",
        lambda m: print(f"render: {slot.scene.id}: {m.text}", file=sys.stderr)
        if m.type in ("warning", "error")
        else None,
    )
    page.goto(scene_url(slot, clip, fmt, words, clips))
    page.wait_for_function("window.JB && (window.JB.isReady || window.JB.failure)", timeout=30_000)
    failure = page.evaluate("JB.failure")
    if failure:
        sys.exit(f"render: scene {slot.scene.id} failed to build: {failure}")
    if errors:
        sys.exit(f"render: scene {slot.scene.id} threw: {errors[0]}")
    return page


def render_slot(
    browser,
    slot: Slot,
    clip: float,
    fmt: str,
    fps: int,
    target: Path,
    scale: float = 1.0,
    words: Sequence[Word] = (),
    clips: Mapping[str, list] | None = None,
) -> None:
    page = open_scene(browser, slot, clip, fmt, scale, words, clips)
    frames = round(slot.length * fps)
    target.parent.mkdir(parents=True, exist_ok=True)
    encoder = subprocess.Popen(
        [
            "ffmpeg",
            "-v",
            "error",
            "-y",
            "-f",
            "image2pipe",
            "-framerate",
            str(fps),
            "-i",
            "-",
            "-c:v",
            "libx264",
            "-preset",
            "medium",
            "-crf",
            "16",
            "-pix_fmt",
            "yuv420p",
            target,
        ],
        stdin=subprocess.PIPE,
    )
    assert encoder.stdin is not None
    for index in range(frames):
        page.evaluate("t => JB.seek(t)", index / fps)
        # JPEG at this quality is indistinguishable after H.264, and Chrome
        # encodes it several times faster than PNG.
        encoder.stdin.write(page.screenshot(type="jpeg", quality=95))
        if index % (fps * 5) == 0:
            print(f"  {slot.scene.id}: {index}/{frames}", flush=True)
    encoder.stdin.close()
    if encoder.wait() != 0:
        sys.exit(f"render: ffmpeg failed on {slot.scene.id}")
    page.close()


def assemble(
    episode: Episode,
    slots: Sequence[Slot],
    parts: Sequence[Path],
    target: Path,
    voice: VoiceSource = "tts",
) -> None:
    total = total_length(slots)
    args = ["ffmpeg", "-v", "error", "-y"]
    for part in parts:
        args += ["-i", part]
    for slot in slots:
        args += ["-i", clip_path(episode, slot.scene, voice)]
    music_input = len(parts) + len(slots)
    if episode.music:
        args += ["-i", music_path(episode)]
    chains = []
    video = "[0:v]"
    for index, slot in enumerate(slots[1:], start=1):
        label = f"[v{index}]"
        chains.append(
            f"{video}[{index}:v]xfade=transition=fade:duration={episode.fade}:offset={slot.start:.3f}{label}"
        )
        video = label
    chains.append(f"{video}fade=t=in:st=0:d=0.5,fade=t=out:st={total - 0.7:.3f}:d=0.7[vout]")
    audio = []
    for index, slot in enumerate(slots):
        delay = round(slot.speech_at * 1000)
        chains.append(f"[{len(parts) + index}:a]adelay={delay}|{delay}[a{index}]")
        audio.append(f"[a{index}]")
    speech = f"{''.join(audio)}amix=inputs={len(audio)}:normalize=0,apad,atrim=0:{total:.3f}"
    if episode.music:
        chains.append(f"{speech}[speech]")
        graph, audio_out = music_filter(
            music_input, "[speech]", total, episode.music.volume, episode.music.duck
        )
        chains.append(graph)
    else:
        chains.append(f"{speech},loudnorm=I=-16:TP=-1.5:LRA=11[aout]")
        audio_out = "[aout]"
    args += [
        "-filter_complex",
        ";".join(chains),
        "-map",
        "[vout]",
        "-map",
        audio_out,
        "-c:v",
        "libx264",
        "-preset",
        "slow",
        "-crf",
        "18",
        "-pix_fmt",
        "yuv420p",
        "-r",
        str(episode.fps),
        "-c:a",
        "aac",
        "-b:a",
        "192k",
        "-ar",
        "48000",
        "-movflags",
        "+faststart",
        "-t",
        f"{total:.3f}",
        target,
    ]
    subprocess.run(args, check=True)


def command_tts(episode: Episode, args: argparse.Namespace) -> None:
    ensure_clips(episode, episode.scenes, dry_run=args.dry_run, voice=args.voice)
    if not args.dry_run:
        ensure_words(episode, episode.scenes, args.voice)


def command_voice(episode: Episode, args: argparse.Namespace) -> None:
    media = MEDIA_DIR
    if args.script:
        target = media / args.episode.name / "voice" / "script.txt"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(voice_script(episode.scenes), encoding="utf-8")
        print(target)
    if args.accept:
        known = {s.id: s for s in episode.scenes}
        unknown = [sid for sid in args.accept if sid not in known]
        if unknown:
            sys.exit(f"render: no such scene: {', '.join(unknown)}")
        for sid in args.accept:
            flac = own_voice_path(media, episode.directory, sid)
            if not flac.is_file():
                sys.exit(f"render: no recording for {sid}: save it as {flac}")
        for sid in args.accept:
            stamp = own_voice_path(media, episode.directory, sid).with_suffix(".txt")
            stamp.write_text(known[sid].narration, encoding="utf-8")
            print(f"accepted {sid} (stamp {own_voice_stamp(known[sid].narration)})")
    if args.check:
        status = voice_status(media, episode.directory, episode.scenes)
        for sid, state in status.items():
            print(f"{sid}: {state}")
        if voice_problems(status):
            sys.exit(1)


def command_preview(episode: Episode, args: argparse.Namespace) -> None:
    from playwright.sync_api import sync_playwright

    scene = next((s for s in episode.scenes if s.id == args.scene), None)
    if scene is None:
        sys.exit(
            f"render: no scene {args.scene}; scenes: {', '.join(s.id for s in episode.scenes)}"
        )
    if args.voice == "own":
        require_own_voice(episode, [scene])
        transcode_own(episode, scene)
    clip = durations(episode, [scene], estimate=True, voice=args.voice)
    slot = timeline([scene], clip, pad=episode.pad, fade=0.0)[0]
    words = load_words(episode, scene, args.voice)
    frames = scene_clips(episode, scene)
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=chrome(), args=["--no-sandbox"])
        if args.at is not None:
            page = open_scene(browser, slot, clip[scene.id], args.format, args.scale, words, frames)
            page.evaluate("t => JB.seek(t)", args.at)
            target = out_dir(REPO, args.episode) / "preview" / f"{scene.id}-{args.format}-{args.at:g}s.png"
            target.parent.mkdir(parents=True, exist_ok=True)
            page.screenshot(path=str(target))
        else:
            target = out_dir(REPO, args.episode) / "preview" / f"{scene.id}-{args.format}.mp4"
            render_slot(
                browser, slot, clip[scene.id], args.format, args.fps, target, args.scale, words, frames
            )
        browser.close()
    print(target)


def part_key(
    episode: Episode,
    slot: Slot,
    clip: float,
    fmt: str,
    words: Sequence[Word],
    clips: Mapping[str, list] | None = None,
) -> str:
    """What a rendered scene depends on: its page, the shared runtime, the
    episode's assets, its timing and its params. Not the news crops a page
    may show from website/assets/; render with --fresh after changing one."""
    digest = hashlib.sha256()
    files = [slot.scene.page, *sorted((HERE / "lib").iterdir())]
    files += sorted((slot.scene.page.parent.parent / "assets").iterdir())
    for path in files:
        digest.update(path.name.encode() + b"\0" + path.read_bytes())
    timing = (
        fmt,
        episode.fps,
        round(slot.length, 3),
        round(slot.speech_at - slot.start, 3),
        round(clip, 3),
        json.dumps(slot.scene.params, sort_keys=True),
        [(w.text, round(w.start, 3), round(w.end, 3)) for w in words],
        json.dumps(clips or {}, sort_keys=True),  # frame dirs are named by clip content hash
    )
    digest.update(repr(timing).encode())
    return digest.hexdigest()[:16]


def _render_part(job: tuple[Slot, float, str, int, Path, list[Word], dict[str, list]]) -> str:
    """One scene in its own process with its own Chrome (fork: Playwright is
    never started in the parent)."""
    from playwright.sync_api import sync_playwright

    slot, clip, fmt, fps, target, words, frames = job
    partial = target.with_suffix(".part.mp4")
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=chrome(), args=["--no-sandbox"])
        render_slot(browser, slot, clip, fmt, fps, partial, words=words, clips=frames)
        browser.close()
    partial.replace(target)
    return slot.scene.id


def command_render(episode: Episode, args: argparse.Namespace) -> None:
    if episode.music:
        music_path(episode)  # exits naming the file before any paid or slow step
    scenes = episode.for_format(args.format)
    ensure_clips(episode, scenes, dry_run=False, voice=args.voice)
    ensure_words(episode, scenes, args.voice)
    clips = durations(episode, scenes, voice=args.voice)
    slots = timeline(scenes, clips, pad=episode.pad, fade=episode.fade)
    words = {s.id: load_words(episode, s, args.voice) for s in scenes}
    frames = {s.id: scene_clips(episode, s) for s in scenes}
    parts_dir = CACHE / "parts" / args.episode.name / args.format
    parts_dir.mkdir(parents=True, exist_ok=True)
    keys = [
        part_key(episode, s, clips[s.scene.id], args.format, words[s.scene.id], frames[s.scene.id])
        for s in slots
    ]
    parts = [parts_dir / f"{s.scene.id}-{key}.mp4" for s, key in zip(slots, keys, strict=True)]
    jobs = [
        (slot, clips[slot.scene.id], args.format, episode.fps, part, words[slot.scene.id], frames[slot.scene.id])
        for slot, part in zip(slots, parts, strict=True)
        if args.fresh or not part.is_file()
    ]
    print(f"render: {len(slots) - len(jobs)} scenes unchanged, {len(jobs)} to render", flush=True)
    if jobs:
        workers = max(1, min(args.jobs, len(jobs)))
        with multiprocessing.get_context("fork").Pool(workers) as pool:
            for scene_id in pool.imap_unordered(_render_part, jobs):
                print(f"render: {scene_id} done", flush=True)
    out = out_dir(REPO, args.episode)
    out.mkdir(parents=True, exist_ok=True)
    target = out / output_name(args.format, args.voice, "mp4")
    assemble(episode, slots, parts, target, args.voice)
    srt = captions(slots, clips)
    (out / output_name(args.format, args.voice, "srt")).write_text(srt, encoding="utf-8")
    english = english_subs_path(MEDIA_DIR, args.episode.name, args.format, args.voice)
    english.parent.mkdir(parents=True, exist_ok=True)
    english.write_text(srt, encoding="utf-8")
    print(f"{target} ({total_length(slots):.1f}s)")


def main(argv: Sequence[str] | None = None) -> None:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--episode", type=Path, default=None, help="episode directory")
    # Also accepted after the command (`tts --dry-run --episode DIR`).
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--episode", type=Path, default=argparse.SUPPRESS, help="episode directory")
    commands = parser.add_subparsers(dest="command", required=True)
    tts = commands.add_parser("tts", parents=[common], help="generate missing narration clips")
    tts.add_argument("--dry-run", action="store_true", help="count, call nothing")
    voice_help = "narration source: generated (tts) or the recordings in .local/video-media (own)"
    tts.add_argument("--voice", choices=("tts", "own"), default="tts", help=voice_help)
    preview = commands.add_parser("preview", parents=[common], help="render one scene")
    preview.add_argument("scene")
    preview.add_argument("--format", choices=sorted(VIEWPORTS), default="main")
    preview.add_argument("--at", type=float, help="a single frame at this scene time, as PNG")
    preview.add_argument("--voice", choices=("tts", "own"), default="tts", help=voice_help)
    preview.add_argument("--fps", type=int, default=15)
    preview.add_argument("--scale", type=float, default=0.5)
    render = commands.add_parser("render", parents=[common], help="render a whole format")
    render.add_argument("format", choices=sorted(VIEWPORTS))
    render.add_argument("--jobs", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    render.add_argument("--voice", choices=("tts", "own"), default="tts", help=voice_help)
    render.add_argument("--fresh", action="store_true", help="re-render unchanged scenes too")
    voice = commands.add_parser("voice", parents=[common], help="own-voice recordings")
    voice.add_argument("--script", action="store_true", help="write script.txt to read aloud")
    voice.add_argument("--check", action="store_true", help="status per scene; exit 1 if any bad")
    voice.add_argument("--accept", nargs="+", metavar="SCENE", help="stamp recordings as current")
    args = parser.parse_args(argv)
    if args.command == "voice" and not (args.script or args.check or args.accept):
        parser.error("voice: give --script, --check or --accept SCENE...")
    args.episode = args.episode or default_episode()
    try:
        episode = load_episode(args.episode / "script.yaml")
    except (OSError, ValueError) as error:
        sys.exit(f"render: {error}")
    {
        "tts": command_tts,
        "preview": command_preview,
        "render": command_render,
        "voice": command_voice,
    }[args.command](
        episode, args
    )


if __name__ == "__main__":
    main()
