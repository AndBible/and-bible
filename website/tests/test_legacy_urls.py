from pathlib import Path

import pytest
import yaml

from sitegen import paths

FIXTURES = Path(__file__).parent / "fixtures"


# Redirects whose target page does not exist yet (data/redirects.yaml `pending`) cannot resolve.
PENDING = set((yaml.safe_load((paths.DATA / "redirects.yaml").read_text()) or {}).get("pending", {}))


def resolves(site: Path, url_path: str) -> bool:
    rel = url_path.lstrip("/")
    candidates = [site / rel / "index.html"] if url_path.endswith("/") else [site / rel]
    return any(c.is_file() for c in candidates)


@pytest.mark.parametrize(
    "url_path",
    [
        pytest.param(u, marks=pytest.mark.xfail(reason="redirect target pending", strict=True))
        if u in PENDING else u
        for u in (FIXTURES / "wp_urls.txt").read_text().split()
    ],
)
def test_wordpress_url_still_resolves(url_path):
    assert paths.SITE.is_dir(), "run `make site` first"
    assert resolves(paths.SITE, url_path), f"{url_path} is gone"
