from pathlib import Path

import pytest

from storeshots.record import screenrecord_cmd, trim_cmd


def pair(argv, flag, value):
    i = argv.index(flag)
    return argv[i + 1] == value


def test_screenrecord_cmd_caps_seconds():
    with pytest.raises(ValueError):
        screenrecord_cmd(181, None)
    argv = screenrecord_cmd(30, "1080x2400")
    assert argv[0] == "screenrecord"
    assert pair(argv, "--time-limit", "30")
    assert pair(argv, "--bit-rate", "12000000")
    assert pair(argv, "--size", "1080x2400")
    assert argv[-1] == "/sdcard/andbible-rec.mp4"
    assert "--size" not in screenrecord_cmd(180, None)


def test_trim_cmd_has_no_audio_and_exact_range():
    argv = trim_cmd(Path("a.mp4"), Path("b.mp4"), 1.5, 4)
    assert pair(argv, "-ss", "1.500") and pair(argv, "-to", "4.000")
    assert "-an" in argv
    assert pair(argv, "-c:v", "libx264")
    assert argv[argv.index("-i") + 1] == "a.mp4"
    assert argv[-1] == "b.mp4"
    assert argv.index("-ss") < argv.index("-i")  # input seek


def test_trim_cmd_rejects_empty_range():
    with pytest.raises(ValueError):
        trim_cmd(Path("a.mp4"), Path("b.mp4"), 4, 4)
