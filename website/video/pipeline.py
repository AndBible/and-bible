# Ported from the JailBee project (website/video), which is GPL-compatible with AndBible.
"""The narrated-video pipeline's pure parts: episodes, the timeline, captions.

No network, no browser and no ffmpeg in here, so the project's test suite can
import it (it has PyYAML but not Playwright). `render.py` does the I/O.
"""

from __future__ import annotations

import difflib
import hashlib
import os
import re
from collections.abc import Mapping, Sequence
from dataclasses import dataclass, field
from pathlib import Path
from typing import Literal

import yaml

FORMATS = ("main", "short")
"""`main` is 1920x1080, `short` 1080x1920; see render.py's VIEWPORTS."""

CAPTION_LINE = 42
"""Characters per caption line before it wraps onto a second one."""


CAPTION_WORDS = 3
"""Most words on screen at once in a burned-in caption."""

CAPTION_CHARS = 18
"""Most characters in one burned-in caption group: it must fit a phone's width."""


class MissingKeyError(Exception):
    """No OpenAI API key in the environment or in the key file."""


@dataclass(frozen=True)
class TTS:
    model: str
    voice: str
    instructions: str


@dataclass(frozen=True)
class Word:
    """One spoken word and when it is said, in seconds from the clip's start."""

    text: str
    start: float
    end: float


@dataclass(frozen=True)
class Scene:
    id: str
    page: Path
    narration: str
    formats: tuple[str, ...]
    params: Mapping[str, object] = field(default_factory=dict)


@dataclass(frozen=True)
class Loop:
    """Loop points of the music track in seconds: the track plays 0 -> end, the
    [start, end) region repeats with an equal-power `crossfade` at each seam, and the
    track's real ending (end -> track length) closes the video."""

    start: float
    end: float
    crossfade: float = 0.5


@dataclass(frozen=True)
class Music:
    """The shared music bed. `file` is relative to the video media directory;
    `volume` is a linear gain, `duck` the sidechain threshold (see music_filter;
    None = no ducking), `loop` optional intro/loop/outro points."""

    file: Path
    volume: float = 0.25
    duck: float | None = 0.35
    loop: Loop | None = None


@dataclass(frozen=True)
class Episode:
    title: str
    tts: TTS
    scenes: tuple[Scene, ...]
    fps: int = 30
    pad: float = 0.6
    fade: float = 0.4
    directory: Path = Path(".")
    """The episode's own directory (where script.yaml is)."""
    music: Music | None = None

    def for_format(self, name: str) -> list[Scene]:
        return [scene for scene in self.scenes if name in scene.formats]


@dataclass(frozen=True)
class Slot:
    """A scene placed on the timeline: where it starts, how long it shows,
    and when its narration starts."""

    scene: Scene
    start: float
    length: float
    speech_at: float


def load_episode(path: Path) -> Episode:
    """Read and validate an episode's script.yaml; ValueError names the problem."""
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    tts_data = data.get("tts") or {}
    for key in ("model", "voice", "instructions"):
        if not str(tts_data.get(key) or "").strip():
            raise ValueError(f"{path}: tts.{key} is required")
    tts = TTS(
        model=str(tts_data["model"]),
        voice=str(tts_data["voice"]),
        instructions=str(tts_data["instructions"]).strip(),
    )
    scenes: list[Scene] = []
    seen: set[str] = set()
    for raw in data.get("scenes") or []:
        scene_id = str(raw.get("id") or "")
        if not scene_id:
            raise ValueError(f"{path}: a scene has no id")
        if scene_id in seen:
            raise ValueError(f"{path}: duplicate scene id '{scene_id}'")
        seen.add(scene_id)
        page = path.parent / str(raw.get("page") or "")
        if not page.is_file():
            raise ValueError(f"{path}: {scene_id}: page {raw.get('page')} does not exist")
        narration = " ".join(str(raw.get("narration") or "").split())
        if not narration:
            raise ValueError(f"{path}: {scene_id}: narration is empty")
        formats = tuple(str(f) for f in raw.get("formats") or [])
        if not formats:
            raise ValueError(f"{path}: {scene_id}: in no format")
        for name in formats:
            if name not in FORMATS:
                raise ValueError(f"{path}: {scene_id}: unknown format '{name}'")
        scenes.append(Scene(scene_id, page, narration, formats, dict(raw.get("params") or {})))
    if not scenes:
        raise ValueError(f"{path}: no scenes")
    music = _load_music(path, data.get("music"))
    return Episode(
        title=str(data.get("title") or path.parent.name),
        tts=tts,
        scenes=tuple(scenes),
        fps=int(data.get("fps", 30)),
        pad=float(data.get("pad", 0.6)),
        fade=float(data.get("fade", 0.4)),
        directory=path.parent,
        music=music,
    )


def _load_music(path: Path, raw: object) -> Music | None:
    if raw is None:
        return None
    if not isinstance(raw, Mapping) or not str(raw.get("file") or "").strip():
        raise ValueError(f"{path}: music.file is required")
    volume = float(raw.get("volume", 0.25))
    if not 0 < volume <= 1:
        raise ValueError(f"{path}: music.volume must be in (0, 1]")
    duck: float | None = 0.35
    if "duck" in raw:  # `duck: null` or 0 switches the sidechain off
        duck = float(raw["duck"]) if raw["duck"] is not None else None
        if duck is not None and duck != 0 and not 0 < duck <= 1:
            raise ValueError(f"{path}: music.duck must be in (0, 1], 0 or null")
        if not duck:
            duck = None
    loop = _load_loop(path, raw.get("loop"))
    return Music(Path(str(raw["file"])), volume, duck, loop)


def _load_loop(path: Path, raw: object) -> Loop | None:
    if raw is None:
        return None
    if not isinstance(raw, Mapping) or "start" not in raw or "end" not in raw:
        raise ValueError(f"{path}: music.loop needs start and end (seconds)")
    loop = Loop(float(raw["start"]), float(raw["end"]), float(raw.get("crossfade", 0.5)))
    try:
        validate_loop(loop)
    except ValueError as error:
        raise ValueError(f"{path}: {error}") from None
    return loop


def validate_loop(loop: Loop) -> None:
    """Static rules for loop points (the track length is checked at render time)."""
    if loop.crossfade <= 0:
        raise ValueError("music.loop.crossfade must be positive")
    if not 0 <= loop.start < loop.end:
        raise ValueError("music.loop needs 0 <= start < end")
    if loop.start < loop.crossfade:
        raise ValueError(f"music.loop.start must be at least the crossfade ({loop.crossfade:g} s)")
    if loop.end - loop.start <= 2 * loop.crossfade:
        raise ValueError("music.loop must be longer than twice the crossfade")


def check_loop(loop: Loop, track_length: float) -> None:
    """Render-time check: the loop must lie inside the track."""
    if loop.end >= track_length:
        raise ValueError(
            f"music.loop.end {loop.end:g} s must be before the end of the track ({track_length:.1f} s)"
        )


@dataclass(frozen=True)
class MusicSegment:
    """One piece of the music timeline: source [src_start, src_end), crossfaded
    over `overlap` seconds with the piece before it (0 = plain join)."""

    src_start: float
    src_end: float
    overlap: float


def music_segments(
    total: float, track_length: float, start: float, end: float, crossfade: float
) -> list[MusicSegment] | None:
    """The segment plan that fills `total` seconds: the intro [0, end], whole loops
    [start - crossfade, end] (each crossfaded into the previous piece, so the seam
    blends the audio before `end` with the audio before `start`), one partial loop
    for the remainder, and the real ending [end, track_length] last, so it finishes
    exactly at `total`. None when the video is shorter than the track (or longer by
    less than a crossfade past a whole number of loops): the caller plays from 0 and fades out."""
    loop_len = end - start
    remaining = total - track_length  # time the loops have to fill
    if remaining < -1e-9:
        return None
    count = int(remaining / loop_len + 1e-9)
    rest = remaining - count * loop_len
    if rest < 1e-6:
        rest = 0.0
    elif rest < crossfade:
        return None  # no room for a partial loop between two crossfades
    plan = [MusicSegment(0.0, end, 0.0)]
    plan += [MusicSegment(start - crossfade, end, crossfade)] * count
    if rest:
        plan.append(MusicSegment(start - crossfade, start + rest, crossfade))
        plan.append(MusicSegment(end - crossfade, track_length, crossfade))
    else:
        plan.append(MusicSegment(end, track_length, 0.0))
    return plan


def _bed_with_loop(
    music_input: int, total: float, volume: float, loop: Loop, track_length: float
) -> str | None:
    plan = music_segments(total, track_length, loop.start, loop.end, loop.crossfade)
    if plan is None:
        return None
    parts = [f"[{music_input}:a]asplit={len(plan)}" + "".join(f"[mseg{i}]" for i in range(len(plan)))]
    for index, seg in enumerate(plan):
        parts.append(
            f"[mseg{index}]atrim=start={seg.src_start:.3f}:end={seg.src_end:.3f},"
            f"asetpts=PTS-STARTPTS[mp{index}]"
        )
    joined = "[mp0]"
    for index, seg in enumerate(plan[1:], start=1):
        out = f"[mj{index}]"
        if seg.overlap:
            parts.append(
                f"{joined}[mp{index}]acrossfade=d={seg.overlap:.3f}:c1=qsin:c2=qsin{out}"
            )
        else:
            parts.append(f"{joined}[mp{index}]concat=n=2:v=0:a=1{out}")
        joined = out
    parts.append(
        f"{joined}atrim=0:{total:.3f},asetpts=N/SR/TB,volume={volume:g},afade=t=in:st=0:d=1.0[bed]"
    )
    return ";".join(parts)


def music_bed(
    music_input: int,
    total: float,
    volume: float,
    loop: Loop | None = None,
    track_length: float | None = None,
) -> str:
    """The graph fragment that makes the music bed (output label `[bed]`) for `total` seconds:
    intro/loop/outro when `loop` and the track length are given and the video is long
    enough, otherwise the whole track looped, faded in over 1.0 s and out over 1.5 s."""
    if loop is not None and track_length is not None:
        graph = _bed_with_loop(music_input, total, volume, loop, track_length)
        if graph is not None:
            return graph
    fade_out = max(total - 1.5, 0.0)
    return (
        f"[{music_input}:a]aloop=loop=-1:size=2147483647,atrim=0:{total:.3f},"
        f"asetpts=N/SR/TB,volume={volume:g},"
        f"afade=t=in:st=0:d=1.0,afade=t=out:st={fade_out:.3f}:d=1.5[bed]"
    )


def music_filter(
    music_input: int,
    speech_label: str,
    total: float,
    volume: float,
    duck: float | None,
    loop: Loop | None = None,
    track_length: float | None = None,
) -> tuple[str, str]:
    """The ffmpeg filter-graph fragment that mixes the music bed under the speech.

    The bed is `music_bed` (looped or intro/loop/outro). With `duck` it is ducked by
    sidechaincompress keyed on the speech (`speech_label` is split into the key and the
    mix leg; `duck` x 0.1 is the linear threshold, ratio 8, 20 ms attack, 400 ms
    release); `duck=None` skips the sidechain and uses `speech_label` once. The bed is
    summed with the speech and loudness-normalised. Returns (graph, output label)."""
    bed = music_bed(music_input, total, volume, loop, track_length)
    mix = "amix=inputs=2:normalize=0:duration=first,loudnorm=I=-16:TP=-1.5:LRA=11[mixout]"
    if not duck:
        graph = ";".join([bed, f"{speech_label}[bed]{mix}"])
    else:
        threshold = max(duck * 0.1, 0.001)
        graph = ";".join(
            [
                f"{speech_label}asplit=2[sp_key][sp_mix]",
                bed,
                f"[bed][sp_key]sidechaincompress=threshold={threshold:.4f}:ratio=8:"
                "attack=20:release=400[ducked]",
                f"[sp_mix][ducked]{mix}",
            ]
        )
    return graph, "[mixout]"


def cache_key(tts: TTS, text: str) -> str:
    """The clip's cache name: changes with anything the audio depends on."""
    digest = hashlib.sha256()
    for part in (tts.model, tts.voice, tts.instructions, text):
        encoded = part.encode("utf-8")
        digest.update(len(encoded).to_bytes(8, "big") + encoded)
    return digest.hexdigest()


def key_file(home: Path) -> Path:
    return home / ".config" / "openai" / "api-key"


def api_key(environ: Mapping[str, str] | None = None, home: Path | None = None) -> str:
    """OPENAI_API_KEY, else ~/.config/openai/api-key. Never logged."""
    environ = os.environ if environ is None else environ
    home = Path.home() if home is None else home
    value = environ.get("OPENAI_API_KEY", "").strip()
    if value:
        return value
    path = key_file(home)
    if path.is_file():
        value = path.read_text(encoding="utf-8").strip()
        if value:
            return value
    raise MissingKeyError(f"no OpenAI API key: set OPENAI_API_KEY or write it to {path}")


def timeline(
    scenes: Sequence[Scene], durations: Mapping[str, float], *, pad: float, fade: float
) -> list[Slot]:
    """Scenes end to end, each `pad` of silence either side of its clip, the
    next starting `fade` before the previous ends so the two can cross-fade."""
    slots: list[Slot] = []
    start = 0.0
    for scene in scenes:
        if scene.id not in durations:
            raise KeyError(f"no narration clip for scene {scene.id}")
        length = durations[scene.id] + 2 * pad
        slots.append(Slot(scene, start, length, start + pad))
        start += length - fade
    return slots


def total_length(slots: Sequence[Slot]) -> float:
    return slots[-1].start + slots[-1].length if slots else 0.0


_SENTENCE = re.compile(r"(?<=[.!?])\s+")


def wrap_caption(text: str) -> str:
    """One line, or two balanced lines split at the space nearest the middle."""
    text = " ".join(text.split())
    if len(text) <= CAPTION_LINE:
        return text
    spaces = [i for i, ch in enumerate(text) if ch == " "]
    if not spaces:
        return text
    middle = min(spaces, key=lambda i: abs(i - len(text) / 2))
    return f"{text[:middle]}\n{text[middle + 1 :]}"


def _stamp(seconds: float) -> str:
    millis = round(seconds * 1000)
    hours, millis = divmod(millis, 3_600_000)
    minutes, millis = divmod(millis, 60_000)
    secs, millis = divmod(millis, 1000)
    return f"{hours:02}:{minutes:02}:{secs:02},{millis:03}"


def captions(slots: Sequence[Slot], durations: Mapping[str, float]) -> str:
    """SRT: one cue per sentence, each sentence's share of its clip in
    proportion to its length."""
    cues: list[str] = []
    for slot in slots:
        sentences = [s for s in _SENTENCE.split(slot.scene.narration) if s]
        total = sum(len(s) for s in sentences)
        at = slot.speech_at
        for sentence in sentences:
            end = at + durations[slot.scene.id] * len(sentence) / total
            cues.append(
                f"{len(cues) + 1}\n{_stamp(at)} --> {_stamp(end)}\n{wrap_caption(sentence)}\n"
            )
            at = end
    return "\n".join(cues)


def group_words(
    words: Sequence[Word], max_words: int = CAPTION_WORDS, max_chars: int = CAPTION_CHARS
) -> list[list[Word]]:
    """Words in order, split into the few at a time a burned-in caption shows:
    a group ends at a sentence end, or when the next word would exceed
    `max_words` or `max_chars`."""
    groups: list[list[Word]] = []
    current: list[Word] = []
    for word in words:
        size = sum(len(w.text) + 1 for w in current) + len(word.text)
        if current and (len(current) >= max_words or size > max_chars):
            groups.append(current)
            current = []
        current.append(word)
        if word.text.endswith((".", "!", "?")):
            groups.append(current)
            current = []
    if current:
        groups.append(current)
    return groups


def _norm(token: str) -> str:
    return re.sub(r"[\W_]+", "", token.lower())


def align_words(narration: str, heard: Sequence[Word]) -> list[Word]:
    """Whisper's timings on the script's own words.

    Whisper writes "12" for "twelve" and drops every full stop, so its text is
    no caption. The narration's tokens keep their spelling and punctuation and
    take the timing of the heard words they match; a stretch that does not
    match one to one ("one point seven" heard as "1.7") is spread evenly over
    the heard span, and a heard word the script lacks is dropped."""
    tokens = narration.split()
    if not heard:
        return []
    matcher = difflib.SequenceMatcher(
        None, [_norm(t) for t in tokens], [_norm(w.text) for w in heard], autojunk=False
    )
    aligned: list[Word] = []
    for op, i1, i2, j1, j2 in matcher.get_opcodes():
        if op == "insert":
            continue
        if op == "delete":
            at = aligned[-1].end if aligned else heard[0].start
            aligned += [Word(t, at, at) for t in tokens[i1:i2]]
            continue
        start, end = heard[j1].start, heard[j2 - 1].end
        if i2 - i1 == j2 - j1:
            aligned += [
                Word(t, w.start, w.end) for t, w in zip(tokens[i1:i2], heard[j1:j2], strict=True)
            ]
            continue
        step = (end - start) / (i2 - i1)
        aligned += [
            Word(t, start + k * step, start + (k + 1) * step) for k, t in enumerate(tokens[i1:i2])
        ]
    return aligned


def out_dir(repo: Path, episode_dir: Path) -> Path:
    """Where an episode's finished videos and captions go, under the repo's .local/."""
    return repo / ".local" / "video-out" / episode_dir.name


VoiceStatus = Literal["ok", "missing", "stale"]
VoiceSource = Literal["tts", "own"]


def own_voice_path(media: Path, episode_dir: Path, scene_id: str) -> Path:
    """The maintainer's recording of one scene: media/<episode>/voice/<scene>.flac."""
    return media / episode_dir.name / "voice" / f"{scene_id}.flac"


def own_voice_stamp(narration: str) -> str:
    """Identifies the narration text a recording was read from (whitespace-insensitive)."""
    normalized = " ".join(narration.split())
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:16]


def voice_status(
    media: Path, episode_dir: Path, scenes: Sequence[Scene]
) -> dict[str, VoiceStatus]:
    """Per scene: `missing` (no FLAC), `stale` (no stamp, or the stamp's text is
    not the script's current narration), else `ok`."""
    result: dict[str, VoiceStatus] = {}
    for scene in scenes:
        flac = own_voice_path(media, episode_dir, scene.id)
        stamp = flac.with_suffix(".txt")
        if not flac.is_file():
            result[scene.id] = "missing"
        elif not stamp.is_file() or own_voice_stamp(
            stamp.read_text(encoding="utf-8")
        ) != own_voice_stamp(scene.narration):
            result[scene.id] = "stale"
        else:
            result[scene.id] = "ok"
    return result


def voice_problems(status: Mapping[str, str]) -> str:
    """The scenes that are not `ok`, each with its status, for an error message."""
    return ", ".join(f"{sid} ({state})" for sid, state in status.items() if state != "ok")


def own_clip_key(flac: bytes) -> str:
    """The transcoded own-voice clip's cache name: only the recording's bytes
    matter, so re-recording one scene invalidates only that scene."""
    return hashlib.sha256(flac).hexdigest()


def voice_script(scenes: Sequence[Scene]) -> str:
    """The text to read aloud: one block per scene, its id then its narration."""
    return "\n".join(f"[{s.id}]\n{s.narration}\n" for s in scenes)


SHORT_LAYOUTS = ("full", "framed")
"""How a short shows the app: `full` (the clip full-bleed, no device frame, the
default) or `framed` (a phone in a device frame under the scene's heading)."""

DEFAULT_SHORT_LAYOUT = "full"


def page_layout(fmt: str, short_layout: str | None = None) -> str:
    """The layout a scene page is opened with (its `layout` URL parameter):
    the short's chosen layout, or `framed` for the main video, which has one."""
    if fmt != "short":
        return "framed"
    layout = short_layout or DEFAULT_SHORT_LAYOUT
    if layout not in SHORT_LAYOUTS:
        raise ValueError(f"unknown short layout '{layout}'; one of {', '.join(SHORT_LAYOUTS)}")
    return layout


def output_name(fmt: str, voice: str, suffix: str, layout: str | None = None) -> str:
    """e.g. main-tts.mp4, short-own.srt; a framed short is short-tts-framed.mp4.
    The default layout keeps the plain name."""
    tag = "-framed" if fmt == "short" and page_layout(fmt, layout) == "framed" else ""
    return f"{fmt}-{voice}{tag}.{suffix}"


def english_subs_path(media: Path, episode: str, fmt: str, voice: str) -> Path:
    """Where a render's English SRT is kept for translation: MEDIA_DIR/<episode>/subs/<fmt>-<voice>.en.srt."""
    return media / episode / "subs" / output_name(fmt, voice, "en.srt")


def clip_frame_index(t: float, start: float, fps: int, frames: int) -> int:
    """0-based frame of a pre-decoded clip to show at scene time `t`: the clip
    starts at `start`, holds frame 0 before it and its last frame after its end.
    Rounds to the nearest frame, so float noise in `t` never skips one. The
    same formula is in runtime.js (`JB.clips`)."""
    if frames <= 0:
        return 0
    index = int((t - start) * fps + 0.5) if t > start else 0
    return min(index, frames - 1)


_CLIP_TAG = re.compile(r"<img\b[^>]*>", re.IGNORECASE)
_DATA_CLIP = re.compile(r"""\bdata-clip\s*=\s*(?:"([^"]*)"|'([^']*)')""")


def scene_clip_paths(page: Path) -> list[str]:
    """The distinct `data-clip` paths of a scene page's `<img>` tags, in page order."""
    found: list[str] = []
    for tag in _CLIP_TAG.findall(page.read_text(encoding="utf-8")):
        match = _DATA_CLIP.search(tag)
        path = (match.group(1) or match.group(2)) if match else None
        if path and path not in found:
            found.append(path)
    return found
