"""Filesystem locations and site-wide constants for the andbible.org generator."""

from __future__ import annotations

from pathlib import Path

WEBSITE = Path(__file__).resolve().parent.parent
REPO = WEBSITE.parent
CONTENT = WEBSITE / "content"
DATA = WEBSITE / "data"
MEDIA = WEBSITE / "media"
TEMPLATES = WEBSITE / "templates"
ASSETS = WEBSITE / "assets"
THEME = WEBSITE / "theme"
BUILD = WEBSITE / "_build"
SITE = WEBSITE / "_site"

BASE_URL = "https://andbible.org"
DEFAULT_LANG = "en"
