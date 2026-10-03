import json
from datetime import date

import pytest

from sitegen import releases


def rel(tag, name, day, prerelease=False, draft=False):
    return {"tag_name": tag, "name": name, "published_at": f"{day}T10:00:00Z", "prerelease": prerelease, "draft": draft}


RAW = [
    rel("test-1118", "Test release 5.1.1118", "2026-09-26", True),
    rel("production-1098", "Release 5.1.1098", "2026-05-29"),
    rel("beta-1090", "Release 5.1.1090-beta", "2026-04-01"),
    rel("alpha-1089", "Release 5.1.1089-alpha", "2026-03-30"),
    rel("production-741", "Release 5.0.741", "2023-11-01"),
    rel("production-640", "Release 4.0.640", "2022-02-24"),
    rel("v3.2.343", "3.2.343", "2022-02-22"),  # late backport, after 4.0
    rel("production-600", "Release 4.0.600", "2021-09-01", draft=True),
    rel("build-02.13.00", "Release 2.13.0", "2018-12-17"),
    rel("build-1234-dev", "Beta build-1234-dev", "2019-01-01"),
    rel("build-50", "Fix #1", "2019-01-02"),
]


@pytest.mark.parametrize("day,expected", [
    (date(2026, 10, 3), "5.1"),
    (date(2026, 5, 29), "5.1"),       # same day counts
    (date(2026, 5, 28), "5.0"),       # alpha/beta/test of 5.1 do not
    (date(2023, 10, 31), "4.0"),
    (date(2022, 2, 23), "3.2"),       # 4.0.640 not out yet, the backport is
    (date(2021, 9, 2), "2.13"),         # the 4.0 dated 2021-09-01 is a draft
    (date(2018, 12, 16), None),
])
def test_version_on(day, expected):
    assert releases.version_on(day, releases.public_releases(RAW)) == expected


def test_backport_does_not_lower_the_version():
    assert releases.version_on(date(2022, 3, 1), releases.public_releases(RAW)) == "4.0"


def test_fetch_stops_once_the_day_is_covered():
    calls = []

    def fetcher(url):
        calls.append(url)
        page = int(url.rsplit("=", 1)[1])
        filler = [rel(f"beta-{page}-{i}", "Release 9.9.9-beta", "2026-09-01") for i in range(99)]
        return json.dumps(([rel("production-1117", "Release 5.1.1117", "2026-08-29")] if page == 1 else
                           [rel("production-741", "Release 5.0.741", "2023-11-01")]) + filler).encode()

    assert releases.fetch_version(date(2026, 9, 5), fetcher) == "5.1"
    assert len(calls) == 1
    calls.clear()
    assert releases.fetch_version(date(2024, 1, 1), fetcher) == "5.0"  # nothing on page 1 is old enough: page 2 is read
    assert len(calls) == 2


def test_fetch_rejects_an_error_answer():
    with pytest.raises(ValueError, match="unexpected"):
        releases.fetch_version(date(2026, 1, 1), lambda url: b'{"message": "rate limited"}')
