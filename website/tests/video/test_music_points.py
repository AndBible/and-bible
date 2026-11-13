"""Loop-point suggestions on synthetic signals."""

from __future__ import annotations

import importlib.util
import math
import random
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
_SPEC = importlib.util.spec_from_file_location(
    "video_music_points", REPO / "website" / "video" / "music_points.py"
)
assert _SPEC is not None and _SPEC.loader is not None
mp = importlib.util.module_from_spec(_SPEC)
sys.modules["video_music_points"] = mp
_SPEC.loader.exec_module(mp)

RATE = 4000


def _beat_signal(bpm: float, bars: int, accent_every: int = 4, seed: int = 1) -> list[float]:
    """A click track: a decaying noise burst per beat, louder on the bar's first beat."""
    rng = random.Random(seed)
    beat = int(RATE * 60 / bpm)
    out: list[float] = []
    for n in range(bars * accent_every):
        gain = 1.0 if n % accent_every == 0 else 0.5
        out += [gain * math.exp(-k / 150) * rng.uniform(-1, 1) for k in range(beat)]
    return out


def test_tempo_is_estimated_from_a_click_track() -> None:
    bpm, _ = mp.analyze(_beat_signal(120, bars=24), RATE, hop=64, bar_lengths=(4,))
    assert abs(bpm - 120) < 3


def test_bar_grid_finds_the_accented_downbeat() -> None:
    samples = _beat_signal(120, bars=24)
    feats = mp.frame_features(samples, 64)
    onset = mp.onset_curve(feats)
    period = mp.refine_period(onset, mp.estimate_period(onset, RATE / 64))
    first, bar = mp.bar_grid(onset, period)
    assert abs(bar - 4 * period) < 1e-9
    beat = RATE * 0.5 / 64  # frames per beat at 120 BPM
    assert abs((first % (4 * beat)) / (4 * beat) - 0) < 0.08 or abs((first % (4 * beat)) / (4 * beat) - 1) < 0.08


def test_ranking_prefers_a_periodic_repeat_over_a_misaligned_pair() -> None:
    period = 100
    features = [(math.sin(2 * math.pi * i / period), (i % period) / period) for i in range(1200)]
    ranked = mp.rank_pairs(features, [(300, 700), (300, 637), (300, 450)], width=50)
    assert [(s, e) for _, s, e in ranked][0] == (300, 700)
    assert ranked[0][0] < 1e-9 < ranked[1][0]


def test_ranking_ignores_pairs_without_room_for_the_window() -> None:
    features = [(0.0, 0.0)] * 100
    assert mp.rank_pairs(features, [(10, 80), (30, 200)], width=50) == []


def test_candidates_are_bar_aligned_and_inside_the_middle_section() -> None:
    bars = list(range(0, 1000, 25))  # 40 bars of 25 frames
    pairs = mp.candidate_pairs(bars, (4, 8), 1000)
    assert pairs
    for start, end in pairs:
        assert start in bars and end in bars
        assert (end - start) in (100, 200)
        assert 200 <= start and end <= 800


def test_analyze_ranks_a_true_repeat_first() -> None:
    """16 bars of music A (a 4-bar riff, repeated) with a noisy bar-aligned section in the middle:
    candidates whose before-end/before-start windows both lie in the riff win over those
    comparing riff with noise."""
    rng = random.Random(7)
    bpm, beat = 120, RATE // 2
    bar = 4 * beat
    samples: list[float] = []
    for b in range(40):
        if 14 <= b < 18:  # a different texture
            samples += [rng.uniform(-0.3, 0.3) for _ in range(bar)]
        else:
            riff = b % 4
            for n in range(4):
                gain = 1.0 if n == 0 else 0.5
                f = 80 + 40 * ((riff + n) % 3)
                samples += [gain * math.sin(2 * math.pi * f * k / RATE) * math.exp(-k / 400) for k in range(beat)]
    tempo, found = mp.analyze(samples, RATE, hop=64, bar_lengths=(4, 8), top=3)
    assert abs(tempo - 120) < 4
    assert found and found == sorted(found, key=lambda c: c.score)
    best = found[0]
    # The riff period is 4 bars, so a good loop length is a multiple of 4 bars
    assert best.bars % 4 == 0
    assert best.score < 0.5
