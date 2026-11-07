import subprocess
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[3]
STYLE = REPO / "store-screenshots" / "style"


@pytest.fixture(scope="session", autouse=True)
def style_assets():
    """Fonts are not committed; fetch them from the system fonts like run.sh does."""
    if not (STYLE / "fonts" / "NotoSans-Bold.ttf").exists():
        subprocess.run([str(STYLE / "fetch-fonts.sh")], check=True)
