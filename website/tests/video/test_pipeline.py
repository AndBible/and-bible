"""The narrated-video pipeline's pure parts: no network, no browser, no ffmpeg."""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[3]
_SPEC = importlib.util.spec_from_file_location(
    "video_pipeline", REPO / "website" / "video" / "pipeline.py"
)
assert _SPEC is not None and _SPEC.loader is not None
pipeline = importlib.util.module_from_spec(_SPEC)
sys.modules["video_pipeline"] = pipeline
_SPEC.loader.exec_module(pipeline)


def _episode(tmp_path: Path, body: str) -> Path:
    scenes = tmp_path / "scenes"
    scenes.mkdir(exist_ok=True)
    for name in ("open.html", "dash.html"):
        (scenes / name).write_text("<html></html>")
    path = tmp_path / "script.yaml"
    path.write_text(body)
    return path


GOOD = """\
title: Test episode
tts:
  model: gpt-4o-mini-tts-2025-12-15
  voice: marin
  instructions: Calm.
scenes:
  - id: open
    page: scenes/open.html
    formats: [main, short]
    narration: First words. Other words!
  - id: dash
    page: scenes/dash.html
    formats: [main]
    narration: Only in the main video.
"""


def test_an_episode_loads_with_its_defaults(tmp_path: Path) -> None:
    episode = pipeline.load_episode(_episode(tmp_path, GOOD))

    assert episode.title == "Test episode"
    assert episode.tts.voice == "marin" and episode.tts.instructions == "Calm."
    assert [s.id for s in episode.scenes] == ["open", "dash"]
    assert episode.scenes[0].page == tmp_path / "scenes" / "open.html"
    assert episode.scenes[0].formats == ("main", "short")
    assert episode.fps == 30 and episode.pad == pytest.approx(0.6)
    assert [s.id for s in episode.for_format("short")] == ["open"]


@pytest.mark.parametrize(
    ("change", "message"),
    [
        (("scenes/dash.html", "scenes/missing.html"), "dash: page scenes/missing.html"),
        (("narration: Only in the main video.", "narration: ' '"), "dash: narration is empty"),
        (("formats: [main]\n", "formats: [tall]\n"), "dash: unknown format 'tall'"),
        (("formats: [main]\n", "formats: []\n"), "dash: in no format"),
        (("id: dash", "id: open"), "duplicate scene id 'open'"),
        (("  voice: marin\n", ""), "tts.voice"),
    ],
)
def test_a_broken_episode_is_refused_naming_the_problem(
    tmp_path: Path, change: tuple[str, str], message: str
) -> None:
    path = _episode(tmp_path, GOOD.replace(*change))

    with pytest.raises(ValueError, match=message.replace("(", r"\(")):
        pipeline.load_episode(path)


def test_the_cache_key_follows_everything_the_audio_depends_on() -> None:
    tts = pipeline.TTS(model="m", voice="v", instructions="i")
    key = pipeline.cache_key(tts, "Hello.")

    assert key == pipeline.cache_key(pipeline.TTS(model="m", voice="v", instructions="i"), "Hello.")
    assert len(key) == 64
    others = {
        pipeline.cache_key(pipeline.TTS(model="m2", voice="v", instructions="i"), "Hello."),
        pipeline.cache_key(pipeline.TTS(model="m", voice="v2", instructions="i"), "Hello."),
        pipeline.cache_key(pipeline.TTS(model="m", voice="v", instructions="i2"), "Hello."),
        pipeline.cache_key(tts, "Hello!"),
    }
    assert key not in others and len(others) == 4
    # Field boundaries matter: moving a character between fields is a new key.
    assert pipeline.cache_key(
        pipeline.TTS(model="ab", voice="c", instructions="i"), "x"
    ) != pipeline.cache_key(pipeline.TTS(model="a", voice="bc", instructions="i"), "x")


def test_the_key_comes_from_the_environment_before_the_file(tmp_path: Path) -> None:
    keyfile = tmp_path / ".config" / "openai" / "api-key"
    keyfile.parent.mkdir(parents=True)
    keyfile.write_text("sk-file\n")

    assert pipeline.api_key({"OPENAI_API_KEY": "sk-env"}, tmp_path) == "sk-env"
    assert pipeline.api_key({}, tmp_path) == "sk-file"
    assert pipeline.api_key({"OPENAI_API_KEY": "  "}, tmp_path) == "sk-file"


def test_no_key_names_both_places_to_put_one(tmp_path: Path) -> None:
    with pytest.raises(pipeline.MissingKeyError) as caught:
        pipeline.api_key({}, tmp_path)

    assert "OPENAI_API_KEY" in str(caught.value)
    assert str(tmp_path / ".config" / "openai" / "api-key") in str(caught.value)


def test_the_timeline_lays_scenes_end_to_end_with_overlapping_fades(tmp_path: Path) -> None:
    episode = pipeline.load_episode(_episode(tmp_path, GOOD))
    durations = {"open": 4.0, "dash": 2.5}

    slots = pipeline.timeline(episode.for_format("main"), durations, pad=0.5, fade=0.4)

    assert [(s.scene.id, s.start, s.length, s.speech_at) for s in slots] == [
        ("open", 0.0, pytest.approx(5.0), pytest.approx(0.5)),
        ("dash", pytest.approx(4.6), pytest.approx(3.5), pytest.approx(5.1)),
    ]
    assert pipeline.total_length(slots) == pytest.approx(8.1)


def test_a_scene_without_its_clip_cannot_be_timed(tmp_path: Path) -> None:
    episode = pipeline.load_episode(_episode(tmp_path, GOOD))

    with pytest.raises(KeyError, match="dash"):
        pipeline.timeline(episode.scenes, {"open": 1.0}, pad=0.5, fade=0.4)


def test_captions_split_a_scene_by_sentence_in_proportion_to_length(tmp_path: Path) -> None:
    episode = pipeline.load_episode(_episode(tmp_path, GOOD))
    slots = pipeline.timeline(
        episode.for_format("main"), {"open": 3.0, "dash": 2.0}, pad=0.5, fade=0.0
    )

    srt = pipeline.captions(slots, {"open": 3.0, "dash": 2.0})

    assert srt == (
        "1\n00:00:00,500 --> 00:00:02,000\nFirst words.\n\n"
        "2\n00:00:02,000 --> 00:00:03,500\nOther words!\n\n"
        "3\n00:00:04,500 --> 00:00:06,500\nOnly in the main video.\n"
    )


def test_a_long_caption_wraps_onto_two_lines() -> None:
    assert (
        pipeline.wrap_caption("word " * 12)
        == "word word word word word word\nword word word word word word"
    )
    assert pipeline.wrap_caption("Short.") == "Short."


def _words(text: str, step: float = 0.3) -> list[pipeline.Word]:
    return [
        pipeline.Word(word, i * step, i * step + step - 0.05) for i, word in enumerate(text.split())
    ]


def test_group_words_keeps_every_word_in_order() -> None:
    words = _words("Twelve AI agents. Twelve branches. Which one needs you right now?")
    groups = pipeline.group_words(words)
    assert [w for g in groups for w in g] == words


def test_group_words_breaks_after_sentence_end() -> None:
    groups = pipeline.group_words(_words("Twelve AI agents. Twelve branches."))
    assert [" ".join(w.text for w in g) for g in groups] == [
        "Twelve AI agents.",
        "Twelve branches.",
    ]


def test_group_words_caps_group_size() -> None:
    groups = pipeline.group_words(_words("one two three four five six seven"), max_words=3)
    assert all(len(g) <= 3 for g in groups)


def test_group_words_caps_characters() -> None:
    groups = pipeline.group_words(_words("extraordinarily incomprehensible words"), max_chars=20)
    assert [len(g) for g in groups] == [1, 1, 1]


def test_group_words_empty() -> None:
    assert pipeline.group_words([]) == []


def _heard(text: str, step: float = 0.3) -> list[pipeline.Word]:
    return _words(text, step)


def test_align_words_restores_punctuation_and_spelling() -> None:
    heard = _heard("twelve AI agents twelve branches")
    aligned = pipeline.align_words("Twelve AI agents. Twelve branches.", heard)
    assert [w.text for w in aligned] == ["Twelve", "AI", "agents.", "Twelve", "branches."]
    assert [(w.start, w.end) for w in aligned] == [(w.start, w.end) for w in heard]


def test_align_words_replaced_token_takes_narration_text() -> None:
    aligned = pipeline.align_words("Twelve agents.", _heard("12 agents"))
    assert [w.text for w in aligned] == ["Twelve", "agents."]
    assert aligned[0].start == 0.0


def test_align_words_spreads_spelled_out_number_over_its_span() -> None:
    heard = [pipeline.Word("JailBee", 0.0, 0.5), pipeline.Word("1.7", 0.5, 1.4)]
    aligned = pipeline.align_words("JailBee one point seven.", heard)
    assert [w.text for w in aligned] == ["JailBee", "one", "point", "seven."]
    assert aligned[1].start == pytest.approx(0.5)
    assert aligned[3].end == pytest.approx(1.4)
    assert [w.start for w in aligned] == sorted(w.start for w in aligned)


def test_align_words_drops_words_the_script_lacks() -> None:
    aligned = pipeline.align_words("Hello world.", _heard("hello there world"))
    assert [w.text for w in aligned] == ["Hello", "world."]


def test_align_words_empty_transcript_gives_no_words() -> None:
    assert pipeline.align_words("Hello.", []) == []


def test_out_dir_is_per_episode(tmp_path):
    repo = tmp_path / "repo"
    episode = repo / "website" / "video" / "episodes" / "tour-1"
    assert pipeline.out_dir(repo, episode) == repo / ".local" / "video-out" / "tour-1"
