"""Suggest loop points for a music track: tempo, bar grid and similarity ranking.

Pure Python (standard library only) so it is unit-tested without numpy. `render.py music-points FILE`
decodes the track to mono 11 kHz samples with ffmpeg and calls `analyze`. It only suggests: the
maintainer listens and chooses the points.

Method: per-frame features (log RMS and zero-crossing rate) -> onset curve -> tempo by autocorrelation ->
beat phase and downbeat by accent -> bar grid. A candidate (start, end) lies on bar boundaries in the
track's middle section; its score is the mean z-normalised feature distance between the audio just before
`end` and just before `start` (the two windows an equal-power crossfade blends), so lower is better.
"""

from __future__ import annotations

import math
from collections.abc import Sequence
from dataclasses import dataclass

Features = list[tuple[float, float]]
"""Per frame: (log RMS, zero-crossing rate)."""


@dataclass(frozen=True)
class Candidate:
    start: float
    end: float
    bars: int
    score: float


def frame_features(samples: Sequence[float], hop: int) -> Features:
    """(log RMS, ZCR) for each full hop-sized frame."""
    frames: Features = []
    for offset in range(0, len(samples) - hop + 1, hop):
        chunk = samples[offset : offset + hop]
        energy = sum(x * x for x in chunk) / hop
        crossings = sum(1 for a, b in zip(chunk, chunk[1:]) if (a < 0) != (b < 0))
        frames.append((math.log10(energy + 1e-9), crossings / (hop - 1)))
    return frames


def onset_curve(features: Features) -> list[float]:
    """Positive rises of the log RMS, one value per frame."""
    rms = [f[0] for f in features]
    return [0.0] + [max(b - a, 0.0) for a, b in zip(rms, rms[1:])]


def estimate_period(onset: Sequence[float], frame_rate: float, lo: float = 80.0, hi: float = 160.0) -> float:
    """Beat period in frames: the autocorrelation peak of the onset curve in [lo, hi] BPM,
    with a half-weight contribution of twice the lag (bars repeat), refined parabolically."""
    mean = sum(onset) / len(onset)
    centred = [x - mean for x in onset]
    n = len(centred)

    def corr(lag: int) -> float:
        if lag >= n:
            return 0.0
        return sum(centred[i] * centred[i + lag] for i in range(n - lag)) / (n - lag)

    lags = range(int(frame_rate * 60 / hi), int(frame_rate * 60 / lo) + 1)
    scores = {lag: corr(lag) + 0.5 * corr(2 * lag) for lag in lags}
    best = max(scores, key=lambda lag: scores[lag])
    left, right = scores.get(best - 1), scores.get(best + 1)
    if left is None or right is None:
        return float(best)
    denom = left - 2 * scores[best] + right
    shift = 0.5 * (left - right) / denom if denom else 0.0
    return best + max(-1.0, min(1.0, shift))


def _comb(onset: Sequence[float], period: float, phase: float) -> float:
    total = 0.0
    position = phase
    while position < len(onset) - 1:
        total += onset[round(position)]
        position += period
    return total


def refine_period(onset: Sequence[float], period: float, span: float = 0.75, step: float = 0.025) -> float:
    """Nudge the period to the value whose beat comb (at its best phase) collects the most onset
    energy; a tiny period error would otherwise drift by many frames over a whole track."""
    best, best_score = period, -1.0
    for i in range(int(2 * span / step) + 1):
        candidate = period - span + i * step
        score = max(_comb(onset, candidate, ph / 4) for ph in range(int(candidate * 4)))
        if score > best_score:
            best, best_score = candidate, score
    return best


def bar_grid(onset: Sequence[float], period: float, beats_per_bar: int = 4) -> tuple[float, float]:
    """(first bar start in frames, bar length in frames): the beat phase that collects the most
    onset energy, and among its `beats_per_bar` classes the most accented one as the downbeat."""

    def energy_at(position: float) -> float:
        index = round(position)
        return onset[index] if 0 <= index < len(onset) else 0.0

    count = int(len(onset) / period) - 1
    best_phase = max(
        (p / 4 for p in range(int(period * 4))),
        key=lambda ph: sum(energy_at(ph + k * period) for k in range(count)),
    )
    class_energy = [
        sum(energy_at(best_phase + (k * beats_per_bar + c) * period) for k in range(count // beats_per_bar))
        for c in range(beats_per_bar)
    ]
    downbeat = class_energy.index(max(class_energy))
    return best_phase + downbeat * period, period * beats_per_bar


def _normalise(features: Features) -> Features:
    columns = list(zip(*features))
    out = []
    stats = []
    for column in columns:
        mean = sum(column) / len(column)
        std = math.sqrt(sum((x - mean) ** 2 for x in column) / len(column)) or 1.0
        stats.append((mean, std))
    for row in features:
        out.append(tuple((x - m) / s for x, (m, s) in zip(row, stats)))
    return out  # type: ignore[return-value]


def window_distance(features: Features, end: int, start: int, width: int) -> float:
    """Mean absolute feature difference between the `width` frames before `end` and before
    `start` (both must be >= width)."""
    total = 0.0
    for k in range(1, width + 1):
        a, b = features[end - k], features[start - k]
        total += sum(abs(x - y) for x, y in zip(a, b))
    return total / (width * len(features[0]))


def rank_pairs(features: Features, pairs: Sequence[tuple[int, int]], width: int) -> list[tuple[float, int, int]]:
    """Sort (start, end) frame pairs by window distance, best first. Features are used as given."""
    scored = [
        (window_distance(features, end, start, width), start, end)
        for start, end in pairs
        if start >= width and end <= len(features)
    ]
    return sorted(scored)


def candidate_pairs(
    bar_starts: Sequence[int], bar_lengths: Sequence[int], total_frames: int, span: tuple[float, float] = (0.2, 0.8)
) -> list[tuple[int, int]]:
    """Bar-aligned (start, end) pairs inside the middle section [span] of the track."""
    lo, hi = span[0] * total_frames, span[1] * total_frames
    pairs = []
    for i, start in enumerate(bar_starts):
        if start < lo:
            continue
        for bars in bar_lengths:
            if i + bars < len(bar_starts) and bar_starts[i + bars] <= hi:
                pairs.append((start, bar_starts[i + bars]))
    return pairs


def analyze(
    samples: Sequence[float], rate: int, hop: int = 256, bar_lengths: Sequence[int] = (4, 8, 16), top: int = 5
) -> tuple[float, list[Candidate]]:
    """(tempo in BPM, the best `top` candidates, lowest score first)."""
    features = frame_features(samples, hop)
    frame_rate = rate / hop
    onset = onset_curve(features)
    period = refine_period(onset, estimate_period(onset, frame_rate))
    bpm = 60 * frame_rate / period
    first, bar = bar_grid(onset, period)
    bar_starts = []
    position = first
    while position < len(features):
        bar_starts.append(round(position))
        position += bar
    pairs = candidate_pairs(bar_starts, bar_lengths, len(features))
    ranked = rank_pairs(_normalise(features), pairs, width=max(1, round(bar)))
    out: list[Candidate] = []
    for score, start, end in ranked:
        if len(out) >= top:
            break
        length = round((end - start) / bar)
        if any(abs(c.start - start / frame_rate) < 0.5 * bar / frame_rate and c.bars == length for c in out):
            continue  # nearly the same candidate
        out.append(Candidate(start / frame_rate, end / frame_rate, length, score))
    return bpm, out
