"""Proposes one Bible module per locale from a catalog of the app's Sword repositories.

The catalog is a JSON list of module dicts (fields: repo, init, lang, drv, desc, feat, opts, locked, lic,
cat, v11n) built by `storeshots catalog` from every repository's `mods.d.tar.gz`. The table is a proposal
for the maintainer: it only uses generic heuristics, never a per-language module name.
"""
from __future__ import annotations

import io
import json
import re
import tarfile
import urllib.request
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

from .errors import ShotError
from .locales import ANDROID_LOCALES, Locale
from .modules_fetch import parse_repositories

BIBLE_DRIVERS = ("zText", "zText4", "RawText", "RawText4")
NON_BIBLE_CATEGORIES = ("Commentaries", "Dictionaries", "Glossaries", "Maps", "Images", "Daily Devotional",
                        "Lexicons / Dictionaries", "Generic Books", "Utility", "And Bible")

# Repository preference (normalised names, prefix match); unlisted repositories come last.
REPO_ORDER = ["crosswire", "andbible", "andbibleextra", "ebible", "ibt", "stepbible", "wycliffe", "lockman",
              "crosswirebeta"]

# The documented locale -> Sword `Lang` mapping: primary subtags (ISO 639-1/-3 variants) that mean the
# same language as the app's locale. Script and region tags are stripped before the lookup.
SWORD_LANGS: dict[str, set[str]] = {
    "iw": {"he", "heb"}, "in": {"id", "ind"}, "fil": {"fil", "tl", "tgl"}, "nb": {"nb", "no", "nob"},
    "ms": {"ms", "zsm", "zlm", "msa"}, "ne": {"ne", "npi"}, "sw": {"sw", "swh"}, "bn": {"bn", "ben"},
    "ta": {"ta", "tam"}, "te": {"te", "tel"}, "ur": {"ur", "urd"}, "et": {"et", "ekk"},
    "zh-rCN": {"zh", "cmn", "zho"}, "zh-rTW": {"zh", "cmn", "zho"}, "ar": {"ar", "arb"},
    "b+sr+Latn": {"sr", "srp"}, "b+sr+RS": {"sr", "srp"}, "pt-rBR": {"pt", "por"}, "el": {"el", "ell"},
}

NON_LATIN = {"ar", "bg", "el", "hi", "iw", "ja", "ko", "ne", "ru", "th", "uk", "ur", "zh-rCN", "zh-rTW",
             "bn", "ta", "te", "b+sr+RS"}
FOREIGN_SCRIPT = {"ur": re.compile("[\u0900-\u097f]")}  # Urdu written in Devanagari is Hindi, not Urdu
ROMANIZED = re.compile(r"roman|translit|pinyin|latin|deva\b|latn", re.I)
PARTIAL = re.compile(r"testament|\bNT\b|\bOT\b|gospel|evangel|psalm|fragment|\bextracts?\b|\binjil\b|"
                     r"\b(john|james|matthew|mark|luke|romans|genesis)\b|"
                     r"הברית החדשה|новый завет|нов[иі] завіт|新约|新約", re.I)
WHOLE = re.compile(r"\bold\b|entire|complete|whole|full bible", re.I)
SIMPLIFIED = set("圣经译标门们这个时书约来后对发实现关于说话让认为从学国长开问间当")
TRADITIONAL = set("聖經譯標門們這個時書約來後對發實現關於說話讓認為從學國長開問間當")
CYRILLIC = re.compile("[\u0400-\u04ff]")
HEBREW_NT = re.compile("הברית החדשה")


@dataclass
class Row:
    locale: Locale
    module: dict | None
    alternatives: list[dict] = field(default_factory=list)
    fallback: bool = False
    notes: list[str] = field(default_factory=list)


def _norm_repo(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


def repo_rank(repo: str) -> int:
    n = _norm_repo(repo)
    best = len(REPO_ORDER)
    for i, prefix in enumerate(REPO_ORDER):
        if n == prefix or (n.startswith(prefix) and not any(
                n.startswith(p) and len(p) > len(prefix) for p in REPO_ORDER)):
            best = i
            break
    return best


def is_bible(m: dict) -> bool:
    return m["drv"] in BIBLE_DRIVERS and m.get("cat", "") not in NON_BIBLE_CATEGORIES


def lang_tag(m: dict) -> tuple[str, str]:
    """(primary subtag, script tag or '') of a catalog Lang value such as `zh-Hans-CN`."""
    parts = m["lang"].split("-")
    script = next((p for p in parts[1:] if len(p) == 4 and p.isalpha()), "")
    return parts[0].lower(), script


def sword_langs(locale: Locale) -> set[str]:
    return SWORD_LANGS.get(locale.app) or {locale.app.split("-")[0]}


def _text(m: dict) -> str:
    return f"{m['init']} {m['desc']}"


def chinese_script(m: dict) -> str | None:
    """'Hans', 'Hant' or None when the module does not say."""
    _, tag = lang_tag(m)
    if tag in ("Hans", "Hant"):
        return tag
    t = m["desc"]
    if re.search(r"simplified", t, re.I):
        return "Hans"
    if re.search(r"traditional", t, re.I):
        return "Hant"
    s = sum(c in SIMPLIFIED for c in t)
    tr = sum(c in TRADITIONAL for c in t)
    return "Hans" if s > tr else "Hant" if tr > s else None


def serbian_script(m: dict) -> str | None:
    """'Cyrl', 'Latn' or None when the module does not say."""
    _, tag = lang_tag(m)
    if tag in ("Cyrl", "Latn"):
        return tag
    if CYRILLIC.search(m["desc"]):
        return "Cyrl"
    if re.search(r"latin|latn", _text(m), re.I):
        return "Latn"
    # Latin words that Serbian uses: a Latin-script desc with Serbian words is a Latin module
    if re.search(r"sveto pismo|biblija|prevod|novi |stari ", m["desc"], re.I):
        return "Latn"
    return None


def wanted_script(locale: Locale) -> str | None:
    return {"zh-rCN": "Hans", "zh-rTW": "Hant", "b+sr+Latn": "Latn", "b+sr+RS": "Cyrl"}.get(locale.app)


def detected_script(locale: Locale, m: dict) -> str | None:
    if locale.app.startswith("zh"):
        return chinese_script(m)
    if "sr" in locale.app:
        return serbian_script(m)
    return None


def is_partial(m: dict) -> bool:
    d = m["desc"]
    if HEBREW_NT.search(d):
        return True
    return bool(PARTIAL.search(d)) and not WHOLE.search(d)


def license_rank(lic: str) -> int:
    t = lic.lower()
    if "public domain" in t or "cc0" in t or t.startswith("creative commons") or "gpl" in t:
        return 2
    if "free" in t:  # "Free non-commercial", "Freely distributable"
        return 1
    return 0


def year_of(m: dict) -> int:
    found = re.search(r"\b(1[5-9]\d\d|20[0-3]\d)\b", m["desc"])
    return int(found.group(1)) if found else 1950


def candidates(rows: list[dict], locale: Locale) -> list[dict]:
    """Eligible modules for a locale, best first: free of cipher locks, right language and script."""
    langs = sword_langs(locale)
    want = wanted_script(locale)
    found = []
    for m in rows:
        if not is_bible(m) or m["locked"] or lang_tag(m)[0] not in langs:
            continue
        got = detected_script(locale, m)
        if want and got and got != want:
            continue
        if re.search(r"pinyin", _text(m), re.I):
            continue
        found.append(m)
    return sorted(found, key=lambda m: sort_key(m, locale))


def sort_key(m: dict, locale: Locale) -> tuple:
    foreign = FOREIGN_SCRIPT.get(locale.app)
    wrong_script = (locale.app in NON_LATIN and bool(ROMANIZED.search(_text(m)))) or bool(
        foreign and foreign.search(m["desc"]))
    ambiguous = wanted_script(locale) is not None and detected_script(locale, m) is None
    return (wrong_script, is_partial(m), ambiguous, repo_rank(m["repo"]),
            "StrongsNumbers" not in m["feat"], -license_rank(m["lic"]), -year_of(m), m["init"])


def describe(m: dict) -> str:
    return f"{m['init']} ({display_repo(m['repo'])}): {m['desc']}"


def propose(catalog_rows: list[dict], locales: list[Locale] | None = None,
            fallback_inits: tuple[str, ...] = ("NASB", "KJV")) -> list[Row]:
    out = []
    for locale in locales or ANDROID_LOCALES:
        if locale.app == "en":
            en = [m for m in catalog_rows if is_bible(m) and not m["locked"] and lang_tag(m)[0] == "en"
                  and m["init"] in fallback_inits]
            en.sort(key=lambda m: fallback_inits.index(m["init"]))
            out.append(Row(locale, en[0] if en else None, en[1:3], notes=["source language"]))
            continue
        found = candidates(catalog_rows, locale)
        if not found:
            en = sorted((m for m in catalog_rows if is_bible(m) and not m["locked"]
                         and lang_tag(m)[0] == "en" and m["init"] in fallback_inits),
                        key=lambda m: fallback_inits.index(m["init"]))
            out.append(Row(locale, en[0] if en else None, en[1:3], True,
                           ["fallback: en", "needs maintainer review: no installable Bible in this language"]))
            continue
        best = found[0]
        notes = []
        key = sort_key(best, locale)
        if key[0]:
            notes.append("needs maintainer review: only a romanized or other-script module")
        if key[1]:
            notes.append("needs maintainer review: partial Bible (NT or selected books)")
        if key[2]:
            notes.append("script not stated by the module; check")
        same = [m for m in found[1:] if is_partial(m) == key[1] and sort_key(m, locale)[0] == key[0]]
        out.append(Row(locale, best, same[:2], notes=notes))
    return out


def _cell(text: str) -> str:
    return text.replace("|", "\\|").replace("\n", " ")


def to_markdown(rows: list[Row], catalog_note: str) -> str:
    native = sum(1 for r in rows if not r.fallback)
    lines = ["# Module table: Bible per store-screenshot language", "",
             f"Catalog source: {catalog_note}", "",
             f"{native} of {len(rows)} locales have a proposed module in their own language; "
             f"{len(rows) - native} fall back to English. This is a proposal: the maintainer approves or "
             "edits it before `demo-content/modules.yaml` is filled.", "",
             "| App code | Play folder | App Store | Tier | Proposed module | Alternatives | Note |",
             "|---|---|---|---|---|---|---|"]
    for r in rows:
        mod = describe(r.module) if r.module else "none in catalog"
        if r.fallback:
            mod = "fallback: en, " + mod
        alts = "; ".join(describe(a) for a in r.alternatives) or "—"
        lines.append("| " + " | ".join(_cell(c) for c in (
            f"`{r.locale.app}`", r.locale.play or "—", r.locale.appstore or "—", str(r.locale.tier),
            mod, alts, "; ".join(r.notes) or "—")) + " |")
    return "\n".join(lines) + "\n"


# --- catalog from the repositories' mods.d.tar.gz -------------------------------------------------------

def parse_conf(text: str) -> dict | None:
    """One Sword module `.conf` into a catalog row (without `repo`)."""
    section = None
    values: dict[str, list[str]] = {}
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i].rstrip("\r")
        i += 1
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line.startswith("[") and "]" in line:
            section = line[1:line.index("]")]
            continue
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        while value.endswith("\\") and i < len(lines):  # continuation lines
            value = value[:-1] + "\n" + lines[i].rstrip("\r")
            i += 1
        values.setdefault(key.strip(), []).append(value.strip())
    if section is None:
        return None

    def one(key: str) -> str:
        return values.get(key, [""])[-1]

    return {"init": section, "lang": one("Lang"), "drv": one("ModDrv"), "desc": one("Description"),
            "feat": values.get("Feature", []), "opts": values.get("GlobalOptionFilter", []),
            "locked": "CipherKey" in values and not one("CipherKey"), "lic": one("DistributionLicense"),
            "cat": one("Category"), "v11n": one("Versification")}


def display_repo(repo: str) -> str:
    """The repository's name as the app lists it (catalog rows may carry the `repo_key` form, e.g. Lockman__CrossWire_)."""
    from .state import repo_root
    try:
        names = parse_repositories((repo_root() / "app/src/main/res/raw/repositories.txt").read_text())
    except OSError:
        names = {}
    by_key = {repo_key(n): n for n in names}
    return repo if repo in names else by_key.get(repo, repo.replace("_", " ").strip())


def parse_mods_tar(data: bytes, repo: str) -> list[dict]:
    rows = []
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:*") as tar:
        for member in tar.getmembers():
            if not member.isfile() or not member.name.endswith(".conf"):
                continue
            raw = tar.extractfile(member).read()
            try:
                text = raw.decode("utf-8")
            except UnicodeDecodeError:
                text = raw.decode("latin-1")
            row = parse_conf(text)
            if row:
                rows.append({"repo": repo, **row})
    return rows


def repo_key(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9]", "_", name)


def fetch_catalog(repositories_txt: Path) -> list[dict]:
    rows: list[dict] = []
    for line in Path(repositories_txt).read_text().splitlines():
        if "=" not in line:
            continue
        fields = line.split("=", 1)[1].split(",")
        if len(fields) < 5:
            continue
        name, host, rawdir = fields[1], fields[2], fields[4]
        url = f"https://{host}{rawdir}/mods.d.tar.gz"
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "storeshots"})
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = resp.read()
        except OSError as e:
            raise ShotError("catalog", f"cannot download {url}: {e}") from e
        rows += parse_mods_tar(data, repo_key(name))
    return rows


def register(sub) -> None:
    p = sub.add_parser("catalog", help="download every repository's mods.d.tar.gz into a JSON catalog")
    p.add_argument("--out", required=True)
    p.add_argument("--repositories", help="repositories.txt (default: the app's)")
    p.set_defaults(handler=cmd_catalog)
    p = sub.add_parser("modules-table", help="propose a Bible module per locale as a markdown table")
    p.add_argument("--catalog", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--repositories", help="repositories.txt recorded in the header (default: the app's)")
    p.set_defaults(handler=cmd_table)


def _repos_path(arg: str | None) -> Path:
    from .state import repo_root
    return Path(arg) if arg else repo_root() / "app/src/main/res/raw/repositories.txt"


def cmd_catalog(args) -> int:
    rows = fetch_catalog(_repos_path(args.repositories))
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(rows, indent=1, ensure_ascii=False))
    print(f"{len(rows)} modules from the repositories -> {out}")
    return 0


def cmd_table(args) -> int:
    catalog = Path(args.catalog)
    rows = propose(json.loads(catalog.read_text()))
    repos = _repos_path(args.repositories)
    try:
        repos_note = str(repos.relative_to(repos.parents[5]))
    except (ValueError, IndexError):
        repos_note = str(repos)
    names = ", ".join(parse_repositories(repos.read_text())) if repos.exists() else "?"
    stamp = date.fromtimestamp(catalog.stat().st_mtime).isoformat()
    note = (f"`{catalog.name}` built {stamp} from the mods.d.tar.gz of the repositories in `{repos_note}` "
            f"({names}); {len(json.loads(catalog.read_text()))} modules")
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(to_markdown(rows, note))
    native = sum(1 for r in rows if not r.fallback)
    print(f"{native}/{len(rows)} locales with a native module; fallback: "
          + ", ".join(r.locale.app for r in rows if r.fallback) + f" -> {out}")
    return 0
