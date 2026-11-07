"""Where a framed image lives inside the store-screenshots repository."""
from __future__ import annotations

from pathlib import Path

from .locales import Locale
from .targets import Target

_FOLDERS = {
    "play-phone": "phone", "play-seven": "seven-inch", "play-ten": "ten-inch",
    "ios-iphone-69": "iphone-6.9", "ios-iphone-65": "iphone-6.5", "ios-ipad-13": "ipad-13",
    "web-phone": "phone", "web-desktop": "desktop", "web-mac": "mac",
}


def extension(target: Target) -> str:
    return "webp" if target.store == "website" else "png"


def target_folder(target: Target, locale: Locale) -> Path:
    folder = _FOLDERS[target.id]
    if target.store == "play":
        if locale.play is None:
            raise ValueError(f"{locale.app} has no Play listing folder")
        return Path("android") / locale.play / folder
    if target.store == "appstore":
        if locale.appstore is None:
            raise ValueError(f"{locale.app} has no App Store locale (English set is used)")
        return Path("ios") / locale.appstore / folder
    return Path("website") / folder


def store_path(target: Target, locale: Locale, index: int) -> Path:
    """`index` is the 1-based position of the shot among the shots of this target."""
    return target_folder(target, locale) / f"{index:02d}.{extension(target)}"
