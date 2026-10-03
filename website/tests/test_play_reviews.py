import json

from sitegen import play_reviews as pr


def _raw(rows):
    payload = json.dumps([rows])
    return ")]}'\n\n" + json.dumps([["wrb.fr", "UsvDTd", payload]])


def _row(i, name, score, text, ts, thumbs):
    return [i, [name], score, None, text, [ts], thumbs]


ROWS = [
    _row("a", "Ann", 5, "x" * 100, 1_600_000_000, 3),
    _row("b", "Bob", 5, "y" * 100, 1_700_000_000, 9),
    _row("c", "Cy", 4, "z" * 100, 1_700_000_000, 50),
    _row("d", "Di", 5, "short", 1_700_000_000, 80),
]


def test_parse_and_filter_orders_by_helpful():
    got = pr.candidates(_raw(ROWS), 60, 420, 30)
    assert [r.name for r in got] == ["Bob", "Ann"]
    assert got[0].year == 2023 and got[1].year == 2020


def test_limit():
    assert len(pr.candidates(_raw(ROWS), 60, 420, 1)) == 1


def test_shape_change_gives_clear_error(capsys):
    assert pr.main([], fetch=lambda url, body: ")]}'\n\n[[\"nope\"]]") == 1
    assert "shape changed" in capsys.readouterr().err


def test_main_prints_and_never_writes(capsys, tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    assert pr.main(["--limit", "2"], fetch=lambda url, body: _raw(ROWS)) == 0
    out = capsys.readouterr().out
    assert "Bob" in out and "y" * 100 in out
    assert list(tmp_path.iterdir()) == []
