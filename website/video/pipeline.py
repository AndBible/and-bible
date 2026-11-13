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
class Episode:
    title: str
    tts: TTS
    scenes: tuple[Scene, ...]
    fps: int = 30
    pad: float = 0.6
    fade: float = 0.4

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
    return Episode(
        title=str(data.get("title") or path.parent.name),
        tts=tts,
        scenes=tuple(scenes),
        fps=int(data.get("fps", 30)),
        pad=float(data.get("pad", 0.6)),
        fade=float(data.get("fade", 0.4)),
    )


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
