"""Store targets: final image size, expected raw capture size and device frame.

Sources (checked 2026-11-07):
- Apple, "Screenshot specifications"
  https://developer.apple.com/help/app-store-connect/reference/screenshot-specifications
  6.9" iPhone 1320x2868 accepted, 6.5" iPhone 1284x2778, 13" iPad 2064x2752; JPEG or PNG without
  alpha, at most 10 screenshots per locale and size.
- Google Play Console, "Add preview assets to showcase your app": JPEG or 24-bit PNG without
  alpha, 320-3840 px per side, long side at most 2x the short side, 16:9 (or 9:16) for promotion
  eligibility; 2-8 screenshots per device type.
All final images are RGB with no alpha, also the website ones.

Raw sources: Play phone = AVD screenshot_phone (pixel_6_pro, 1440x3120); Play 7" = AVD
screenshot_tablet7 (1200x1920 @320, landscape: 1920x1200); Play 10" = AVD tablet_api36
(pixel_tablet, landscape 2560x1600); iPhone = iPhone 16 Pro Max simulator (1320x2868, the 6.5"
set is the same raw re-framed); iPad = iPad Pro 13-inch (M4) simulator, portrait; desktop =
Linux desktop app window capture; web-mac = the iPad app window on the Mac.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Literal


@dataclass(frozen=True)
class Target:
    id: str
    store: Literal["play", "appstore", "website"]
    size: tuple[int, int]        # final image size
    raw_size: tuple[int, int]    # exact pixel size of the raw capture
    frame: str                   # device frame name in style/frames.yaml
    alpha_allowed: bool = False


def _t(id, store, size, raw, frame):
    return id, Target(id, store, size, raw, frame)


TARGETS: dict[str, Target] = dict([
    _t("play-phone", "play", (1080, 1920), (1440, 3120), "phone"),
    _t("play-seven", "play", (1920, 1080), (1920, 1200), "tablet"),
    _t("play-ten", "play", (2560, 1440), (2560, 1600), "tablet"),
    _t("ios-iphone-69", "appstore", (1320, 2868), (1320, 2868), "iphone"),
    _t("ios-iphone-65", "appstore", (1284, 2778), (1320, 2868), "iphone"),
    _t("ios-ipad-13", "appstore", (2064, 2752), (2064, 2752), "ipad"),
    _t("web-phone", "website", (1080, 1920), (1440, 3120), "phone"),
    _t("web-desktop", "website", (1920, 1200), (1600, 1000), "desktop"),
    _t("web-mac", "website", (1920, 1200), (1600, 1000), "desktop"),
])

PLATFORM_PREFIX = {"android": "play-", "ios": "ios-", "web": "web-"}


def platform_targets(platform: str) -> list[Target]:
    prefix = PLATFORM_PREFIX[platform]
    return [t for tid, t in TARGETS.items() if tid.startswith(prefix)]
