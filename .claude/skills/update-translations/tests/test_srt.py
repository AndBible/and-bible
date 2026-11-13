"""Subtitle helpers: prepare (cue texts out), merge (translated texts + English timings), validate."""
import subprocess
import sys
from pathlib import Path

import yaml

SKILL = Path(__file__).resolve().parents[1]
REPO = SKILL.parents[2]
CUES = [
    ("00:00:00,600 --> 00:00:03,100", "Search starts here."),
    ("00:00:03,500 --> 00:00:06,000", "Tap the magnifier\nin the toolbar."),
    ("00:00:06,500 --> 00:00:08,000", "Done."),
]


def write_srt(path: Path, cues) -> Path:
    path.write_text(
        "\n".join(f"{i}\n{t}\n{x}\n" for i, (t, x) in enumerate(cues, 1)), encoding="utf-8"
    )
    return path


def run(script: str, *args, cwd=REPO):
    return subprocess.run(
        [sys.executable, str(SKILL / script), *map(str, args)],
        capture_output=True, text=True, cwd=cwd,
    )


def run_validate(en, tr):
    return run("srt_validate.py", en, tr)


def test_validate_rejects_cue_count_and_timing_changes(tmp_path):
    en = write_srt(tmp_path / "en.srt", CUES[:2])
    ok = write_srt(tmp_path / "de.srt", [(CUES[0][0], "Die Suche beginnt hier."),
                                         (CUES[1][0], "Tippe auf die Lupe.")])
    merged = write_srt(tmp_path / "x.srt", [("00:00:00,600 --> 00:00:06,000", "Beides.")])
    shifted = write_srt(tmp_path / "y.srt", [("00:00:00,700 --> 00:00:03,100", "a"),
                                             (CUES[1][0], "b")])
    assert run_validate(en, ok).returncode == 0
    r = run_validate(en, merged)
    assert r.returncode == 1 and "cue count" in r.stdout
    r = run_validate(en, shifted)
    assert r.returncode == 1 and "cue 1" in r.stdout


def test_validate_rejects_empty_text_and_accepts_multiline(tmp_path):
    en = write_srt(tmp_path / "en.srt", CUES)
    good = write_srt(tmp_path / "g.srt", [(t, "x\ny") for t, _ in CUES])
    assert run_validate(en, good).returncode == 0
    bad = write_srt(tmp_path / "b.srt", [CUES[0], (CUES[1][0], "ok"), (CUES[2][0], "  ")])
    r = run_validate(en, bad)
    assert r.returncode == 1 and "cue 3" in r.stdout


def test_prepare_has_no_timings_and_keeps_order(tmp_path):
    subs = tmp_path / "ep" / "subs"
    subs.mkdir(parents=True)
    write_srt(subs / "main-tts.en.srt", CUES)
    r = run("srt_prepare.py", subs, "main-tts", "de", cwd=tmp_path)
    assert r.returncode == 0, r.stderr
    out = tmp_path / "tmp" / "translate-srt" / "ep-main-tts.de.yaml"
    text = out.read_text(encoding="utf-8")
    assert "-->" not in text
    data = yaml.safe_load(text)
    assert [c["n"] for c in data["cues"]] == [1, 2, 3]
    assert [c["text"] for c in data["cues"]] == [x for _, x in CUES]
    assert "glossary" in data and data["lang"] == "de"


def test_prepare_tolerates_language_without_glossary(tmp_path):
    subs = tmp_path / "ep" / "subs"
    subs.mkdir(parents=True)
    write_srt(subs / "main-tts.en.srt", CUES)
    r = run("srt_prepare.py", subs, "main-tts", "zz", cwd=tmp_path)
    assert r.returncode == 0, r.stderr
    assert (tmp_path / "tmp" / "translate-srt" / "ep-main-tts.zz.yaml").is_file()


def test_merge_keeps_english_timing_lines_and_validates(tmp_path):
    en = write_srt(tmp_path / "en.srt", CUES)
    cues = tmp_path / "cues.yaml"
    cues.write_text(yaml.safe_dump({"cues": [
        {"n": 1, "text": "Eins"}, {"n": 2, "text": "Zwei\nzeilig"}, {"n": 3, "text": "Drei"}]}),
        encoding="utf-8")
    out = tmp_path / "de.srt"
    assert run("srt_merge.py", en, cues, out).returncode == 0
    assert "Zwei\nzeilig" in out.read_text(encoding="utf-8")
    assert run_validate(en, out).returncode == 0


def test_merge_fails_on_count_mismatch(tmp_path):
    en = write_srt(tmp_path / "en.srt", CUES)
    cues = tmp_path / "cues.yaml"
    cues.write_text(yaml.safe_dump({"cues": [{"n": 1, "text": "Eins"}]}), encoding="utf-8")
    out = tmp_path / "de.srt"
    r = run("srt_merge.py", en, cues, out)
    assert r.returncode == 1 and "cue count" in (r.stdout + r.stderr)
    assert not out.exists()
