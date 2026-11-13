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


# --- narration sources -------------------------------------------------------


def ep_dir(tmp_path: Path) -> Path:
    return tmp_path


def make_episode(tmp_path: Path, scenes: list[str]):
    (tmp_path / "scenes").mkdir(exist_ok=True)
    body = "title: T\ntts:\n  model: m\n  voice: v\n  instructions: Calm.\nscenes:\n"
    for sid in scenes:
        (tmp_path / "scenes" / f"{sid}.html").write_text("<html></html>")
        body += (
            f"  - id: {sid}\n    page: scenes/{sid}.html\n    formats: [main]\n"
            f"    narration: Narration of {sid}.\n"
        )
    (tmp_path / "script.yaml").write_text(body)
    return pipeline.load_episode(tmp_path / "script.yaml")


def write_recording(tmp_path: Path, ep, scene_id: str, text: str, data: bytes = b"flac") -> None:
    flac = pipeline.own_voice_path(tmp_path / "media", ep_dir(tmp_path), scene_id)
    flac.parent.mkdir(parents=True, exist_ok=True)
    flac.write_bytes(data)
    flac.with_suffix(".txt").write_text(text, encoding="utf-8")


def test_own_voice_path_is_per_episode_and_scene(tmp_path):
    path = pipeline.own_voice_path(tmp_path / "media", tmp_path / "tour-1", "hook")
    assert path == tmp_path / "media" / "tour-1" / "voice" / "hook.flac"


def test_own_voice_missing_scene_names_it(tmp_path):
    ep = make_episode(tmp_path, scenes=["hook", "search"])
    write_recording(tmp_path, ep, "hook", text=ep.scenes[0].narration)
    status = pipeline.voice_status(tmp_path / "media", ep_dir(tmp_path), ep.scenes)
    assert status == {"hook": "ok", "search": "missing"}


def test_own_voice_stale_after_text_edit(tmp_path):
    ep = make_episode(tmp_path, scenes=["hook"])
    write_recording(tmp_path, ep, "hook", text="an older narration")
    assert pipeline.voice_status(tmp_path / "media", ep_dir(tmp_path), ep.scenes) == {"hook": "stale"}


def test_own_voice_recording_without_stamp_is_stale(tmp_path):
    ep = make_episode(tmp_path, scenes=["hook"])
    write_recording(tmp_path, ep, "hook", text="x")
    pipeline.own_voice_path(tmp_path / "media", tmp_path, "hook").with_suffix(".txt").unlink()
    assert pipeline.voice_status(tmp_path / "media", tmp_path, ep.scenes) == {"hook": "stale"}


def test_own_voice_stamp_ignores_whitespace_and_tracks_text():
    assert pipeline.own_voice_stamp("a  b\n") == pipeline.own_voice_stamp("a b")
    assert pipeline.own_voice_stamp("a b") != pipeline.own_voice_stamp("a c")
    assert len(pipeline.own_voice_stamp("a b")) == 16


def test_own_clip_key_follows_flac_bytes_only():
    assert pipeline.own_clip_key(b"one") == pipeline.own_clip_key(b"one")
    assert pipeline.own_clip_key(b"one") != pipeline.own_clip_key(b"two")


def test_voice_problems_lists_every_bad_scene():
    text = pipeline.voice_problems({"a": "ok", "b": "missing", "c": "stale"})
    assert "b (missing)" in text and "c (stale)" in text and "ok" not in text


def test_voice_script_has_one_block_per_scene(tmp_path):
    ep = make_episode(tmp_path, scenes=["hook", "search"])
    text = pipeline.voice_script(ep.scenes)
    assert text.index("hook") < text.index("Narration of hook.") < text.index("search")
    assert "Narration of search." in text


def test_output_name_carries_the_voice_source():
    assert pipeline.output_name("main", "tts", "mp4") == "main-tts.mp4"
    assert pipeline.output_name("short", "own", "srt") == "short-own.srt"


def test_output_name_carries_a_non_default_short_layout():
    assert pipeline.output_name("short", "tts", "mp4", "full") == "short-tts.mp4"
    assert pipeline.output_name("short", "tts", "mp4", None) == "short-tts.mp4"
    assert pipeline.output_name("short", "tts", "mp4", "framed") == "short-tts-framed.mp4"
    assert pipeline.output_name("short", "own", "srt", "framed") == "short-own-framed.srt"
    # The main video has one layout; a short-layout option never renames it.
    assert pipeline.output_name("main", "tts", "mp4", "framed") == "main-tts.mp4"
    assert pipeline.output_name("main", "tts", "mp4", "full") == "main-tts.mp4"


def test_page_layout_defaults_and_validates():
    assert pipeline.page_layout("short") == "full"
    assert pipeline.page_layout("short", "framed") == "framed"
    assert pipeline.page_layout("main") == "framed"
    assert pipeline.page_layout("main", "full") == "framed"
    with pytest.raises(ValueError, match="short layout"):
        pipeline.page_layout("short", "tiled")


def test_music_filter_loops_and_fades() -> None:
    graph, label = pipeline.music_filter(5, "[speech]", total=92.4, volume=0.25, duck=0.35)

    assert "aloop=loop=-1" in graph and "atrim=0:92.400" in graph
    assert "afade=t=in:st=0:d=1.0" in graph
    assert "afade=t=out:st=90.900:d=1.5" in graph
    assert "volume=0.25" in graph
    assert "sidechaincompress" in graph and "[speech]" in graph
    assert "[5:a]" in graph and "loudnorm=I=-16:TP=-1.5:LRA=11" in graph
    assert label.startswith("[") and label in graph


def test_the_music_is_mixed_with_the_speech_before_loudnorm() -> None:
    graph, _ = pipeline.music_filter(5, "[speech]", total=10.0, volume=0.25, duck=0.35)

    assert graph.index("amix") < graph.index("loudnorm")


def test_load_episode_music_optional(tmp_path: Path) -> None:
    assert pipeline.load_episode(_episode(tmp_path, GOOD)).music is None
    assert pipeline.load_episode(_episode(tmp_path, GOOD + "music: null\n")).music is None
    ep = pipeline.load_episode(_episode(tmp_path, GOOD + "music: {file: shared/theme.flac}\n"))
    assert ep.music is not None
    assert ep.music.file == Path("shared/theme.flac")
    assert ep.music.volume == 0.25 and ep.music.duck == 0.35
    ep = pipeline.load_episode(
        _episode(tmp_path, GOOD + "music: {file: a.flac, volume: 0.1, duck: 0.5}\n")
    )
    assert ep.music is not None and (ep.music.volume, ep.music.duck) == (0.1, 0.5)


@pytest.mark.parametrize(
    "music", ["{volume: 0.2}", "{file: a.flac, volume: 2}", "{file: a.flac, duck: 2}", "theme.flac",
     "{file: a.flac, loop: {start: 5, end: 5}}", "{file: a.flac, loop: {start: 0, end: 20}}",
     "{file: a.flac, loop: {start: 10, end: 10.8}}", "{file: a.flac, loop: {start: 10}}"]
)
def test_a_broken_music_block_is_refused(tmp_path: Path, music: str) -> None:
    with pytest.raises(ValueError, match="music"):
        pipeline.load_episode(_episode(tmp_path, GOOD + f"music: {music}\n"))


def test_clip_frame_index_bounds():
    assert pipeline.clip_frame_index(0.0, start=1.0, fps=30, frames=90) == 0
    assert pipeline.clip_frame_index(1.0, start=1.0, fps=30, frames=90) == 0
    assert pipeline.clip_frame_index(2.0, start=1.0, fps=30, frames=90) == 30
    assert pipeline.clip_frame_index(99.0, start=1.0, fps=30, frames=90) == 89
    assert pipeline.clip_frame_index(5.0, start=1.0, fps=30, frames=0) == 0


def test_clip_frame_index_rounds_to_nearest_frame():
    # 0.49 frames in -> frame 0; 0.51 frames in -> frame 1 (matches the JS Math.round)
    assert pipeline.clip_frame_index(1.0 + 0.49 / 30, start=1.0, fps=30, frames=90) == 0
    assert pipeline.clip_frame_index(1.0 + 0.51 / 30, start=1.0, fps=30, frames=90) == 1


def test_scene_clip_paths_finds_unique_clips_in_order(tmp_path: Path):
    page = tmp_path / "s.html"
    page.write_text(
        '<img class="clip" data-clip="captures/a.mp4" data-start="x">'
        "<img data-start=\"1\" class='clip' data-clip='captures/b.mp4'>"
        '<img class="clip" data-clip="captures/a.mp4" data-start="2">'
    )
    assert pipeline.scene_clip_paths(page) == ["captures/a.mp4", "captures/b.mp4"]


def test_english_subs_path(tmp_path: Path):
    assert pipeline.english_subs_path(tmp_path, "my-episode", "main", "own") == (
        tmp_path / "my-episode" / "subs" / "main-own.en.srt"
    )


# --- intro / loop / outro ----------------------------------------------------

def _plan(total, track=100.0, start=20.0, end=60.0, cf=0.5):
    return pipeline.music_segments(total, track, start, end, cf)


def _plan_length(plan):
    return sum(s.src_end - s.src_start for s in plan) - sum(s.overlap for s in plan)


def test_plan_long_video_has_intro_loops_and_outro_ending_at_total() -> None:
    plan = _plan(total=250.0)  # intro 60, outro 40 -> 150 s of loop = 3 full loops + 30 s

    assert plan is not None
    assert (plan[0].src_start, plan[0].src_end, plan[0].overlap) == (0.0, 60.0, 0.0)
    assert plan[-1].src_end == 100.0  # the track's real ending, complete
    assert abs(_plan_length(plan) - 250.0) < 1e-9  # outro ends exactly at the video's end
    loops = [s for s in plan[1:-1]]
    assert len(loops) == 4  # 3 full loops and the partial one
    for full in loops[:3]:
        assert (full.src_start, full.src_end, full.overlap) == (19.5, 60.0, 0.5)
    assert plan[-1].overlap == 0.5  # partial loop crossfades into the outro
    assert plan[-1].src_start == 59.5


def test_plan_exact_loops_joins_outro_without_crossfade() -> None:
    plan = _plan(total=100.0 + 2 * 40.0)

    assert plan is not None
    assert [s.overlap for s in plan] == [0.0, 0.5, 0.5, 0.0]
    assert (plan[-1].src_start, plan[-1].src_end) == (60.0, 100.0)
    assert abs(_plan_length(plan) - 180.0) < 1e-9


def test_plan_crossfade_count_is_one_per_loop_plus_the_outro_seam() -> None:
    plan = _plan(total=250.0)
    assert plan is not None
    assert sum(1 for s in plan if s.overlap > 0) == 4 + 1


def test_plan_video_shorter_than_the_track_falls_back() -> None:
    assert _plan(total=99.0) is None
    assert _plan(total=100.0) is not None  # exactly intro+outro: the track as it is


def test_plan_exactly_track_length_is_plain_playback() -> None:
    plan = _plan(total=100.0)
    assert plan is not None
    assert abs(_plan_length(plan) - 100.0) < 1e-9
    assert all(s.overlap == 0 for s in plan)


@pytest.mark.parametrize("total", [100.2, 100.7, 101.0, 140.3, 141.0, 400.123])
def test_plan_always_ends_at_total(total: float) -> None:
    plan = _plan(total=total)
    if plan is None:  # a remainder shorter than a crossfade: plain playback
        assert (total - 100.0) % 40.0 < 0.5
        return
    assert abs(_plan_length(plan) - total) < 1e-9
    assert plan[-1].src_end == 100.0
    assert all(0 <= s.src_start < s.src_end <= 100.0 + 1e-9 for s in plan)


def test_music_filter_loop_graph_crossfades_and_keeps_the_ending() -> None:
    loop = pipeline.Loop(start=20.0, end=60.0, crossfade=0.5)
    graph, label = pipeline.music_filter(
        5, "[speech]", total=250.0, volume=0.35, duck=None, loop=loop, track_length=100.0
    )

    assert graph.count("acrossfade") == 5
    assert "acrossfade=d=0.500:c1=qsin:c2=qsin" in graph
    assert "atrim=start=0.000:end=60.000" in graph
    assert "afade=t=out" not in graph  # the outro is heard complete
    assert "afade=t=in:st=0:d=1.0" in graph
    assert "sidechaincompress" not in graph and "[speech]" in graph and "amix" in graph
    assert "aloop" not in graph and label in graph


def test_music_filter_loop_falls_back_for_a_short_video() -> None:
    loop = pipeline.Loop(start=20.0, end=60.0, crossfade=0.5)
    graph, _ = pipeline.music_filter(
        5, "[speech]", total=50.0, volume=0.35, duck=0.35, loop=loop, track_length=100.0
    )

    assert "acrossfade" not in graph and "atrim=0:50.000" in graph
    assert "afade=t=out:st=48.500:d=1.5" in graph and "sidechaincompress" in graph


def test_music_filter_without_ducking_has_no_sidechain() -> None:
    graph, _ = pipeline.music_filter(5, "[speech]", total=10.0, volume=0.35, duck=None)

    assert "sidechaincompress" not in graph and "asplit" not in graph
    assert "amix" in graph and "loudnorm" in graph


def test_load_music_loop_and_duck_options(tmp_path: Path) -> None:
    ep = pipeline.load_episode(
        _episode(
            tmp_path,
            GOOD + "music: {file: a.wav, volume: 0.35, duck: null,"
            " loop: {start: 30, end: 90, crossfade: 1}}\n",
        )
    )
    assert ep.music is not None and ep.music.duck is None
    assert ep.music.loop == pipeline.Loop(30.0, 90.0, 1.0)
    ep = pipeline.load_episode(_episode(tmp_path, GOOD + "music: {file: a.wav, duck: 0}\n"))
    assert ep.music is not None and ep.music.duck is None and ep.music.loop is None
    ep = pipeline.load_episode(
        _episode(tmp_path, GOOD + "music: {file: a.wav, loop: {start: 30, end: 90}}\n")
    )
    assert ep.music is not None and ep.music.duck == 0.35
    assert ep.music.loop is not None and ep.music.loop.crossfade == 0.5


def test_check_loop_against_the_track_length() -> None:
    pipeline.check_loop(pipeline.Loop(20.0, 60.0, 0.5), 100.0)
    with pytest.raises(ValueError, match="track"):
        pipeline.check_loop(pipeline.Loop(20.0, 160.0, 0.5), 100.0)


def test_plan_remainder_shorter_than_a_crossfade_falls_back() -> None:
    assert _plan(total=140.3) is None  # one loop + 0.3 s: would overrun into the ending
    assert _plan(total=140.5) is not None
