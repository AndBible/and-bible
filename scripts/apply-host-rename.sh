#!/usr/bin/env bash
# Applies the Ios* -> Host* rename from scripts/host-rename-map.tsv (override: env HOST_RENAME_MAP).
#
# Usage: scripts/apply-host-rename.sh [paths...]
#   No paths: every tracked *.kt / *.kts file (git ls-files). With paths: *.kt / *.kts files under
#   them (tracked or not), skipping build/ and .git directories.
#
# What it does (idempotent; a second run changes nothing):
#   1. Rewrites content with a small Kotlin lexer: whole-word identifiers in code and comments
#      (string-template code included) are renamed in one longest-first pass; inside string
#      literals only "<Old>.kt" file names are rewritten, so log tags and messages stay as they are.
#   2. Renames files whose stem is an old name (git mv when tracked, mv otherwise). Target clashes
#      are checked first; on a clash nothing is modified and the exit status is 1.
set -euo pipefail
exec python3 -I - "$@" <<'PY'
import os, re, subprocess, sys

root = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()
args = [os.path.abspath(a) for a in sys.argv[1:]]
map_path = os.environ.get("HOST_RENAME_MAP") or os.path.join(root, "scripts/host-rename-map.tsv")
map_path = os.path.abspath(map_path)
os.chdir(root)

mapping = {}
with open(map_path, encoding="utf-8") as f:
    for line in f:
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 2 or not parts[0].strip() or not parts[1].strip():
            sys.exit("bad map line: %r" % line)
        mapping[parts[0].strip()] = parts[1].strip()
if not mapping:
    sys.exit("empty map: " + map_path)

names = sorted(mapping, key=lambda s: (-len(s), s))
alt = "|".join(re.escape(n) for n in names)
B = r"(?<![A-Za-z0-9_])", r"(?![A-Za-z0-9_])"
code_re = re.compile(B[0] + "(" + alt + ")" + B[1])
file_re = re.compile(B[0] + "(" + alt + r")\.kt" + B[1])

IDENT_START = re.compile(r"[A-Za-z_]")
IDENT = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")


def rewrite(src):
    """Returns src with renames applied, honouring Kotlin lexical structure."""
    segs = []  # (kind, text); kind in code/comment/str
    n = len(src)

    def comment_block(i):
        depth = 0
        j = i
        while j < n:
            if src.startswith("/*", j):
                depth += 1; j += 2
            elif src.startswith("*/", j):
                depth -= 1; j += 2
                if depth == 0:
                    break
            else:
                j += 1
        return j

    def string(i, raw):
        # i is just after the opening quote(s); returns index after the closing quote(s)
        start = i
        while i < n:
            c = src[i]
            if not raw and c == "\\":
                i += 2
                continue
            if raw and src.startswith('"""', i):
                j = i + 3
                while j < n and src[j] == '"':
                    j += 1
                # closing delimiter is the last three quotes of the run
                segs.append(("str", src[start:j - 3]))
                segs.append(("str", '"""'))
                return j
            if not raw and c == '"':
                segs.append(("str", src[start:i + 1]))
                return i + 1
            if c == "$" and i + 1 < n:
                if src[i + 1] == "{":
                    segs.append(("str", src[start:i + 2]))
                    j = code(i + 2, True)
                    segs.append(("str", "}"))
                    i = start = j + 1
                    continue
                m = IDENT.match(src, i + 1)
                if m:
                    segs.append(("str", src[start:i + 1]))
                    segs.append(("code", m.group(0)))
                    i = start = m.end()
                    continue
            i += 1
        segs.append(("str", src[start:i]))
        return n

    def code(i, in_template):
        # Scans code; in a template returns the index of the closing '}' (not consumed).
        start = i
        depth = 0

        def flush(end):
            if end > start:
                segs.append(("code", src[start:end]))

        while i < n:
            c = src[i]
            if src.startswith("//", i):
                flush(i)
                j = src.find("\n", i)
                j = n if j < 0 else j
                segs.append(("comment", src[i:j]))
                i = start = j
            elif src.startswith("/*", i):
                flush(i)
                j = comment_block(i)
                segs.append(("comment", src[i:j]))
                i = start = j
            elif src.startswith('"""', i):
                flush(i)
                segs.append(("str", '"""'))
                i = start = string(i + 3, True)
            elif c == '"':
                flush(i)
                segs.append(("str", '"'))
                i = start = string(i + 1, False)
            elif c == "'":
                j = i + 1
                if j < n and src[j] == "\\":
                    j += 2
                    while j < n and src[j] != "'" and src[j] != "\n":
                        j += 1
                else:
                    j += 1
                if j < n and src[j] == "'":
                    flush(i)
                    segs.append(("str", src[i:j + 1]))
                    i = start = j + 1
                else:
                    i += 1
            elif in_template and c == "{":
                depth += 1; i += 1
            elif in_template and c == "}":
                if depth == 0:
                    flush(i)
                    return i
                depth -= 1; i += 1
            else:
                i += 1
        flush(i)
        return i

    code(0, False)
    out = []
    for kind, text in segs:
        if kind == "str":
            text = file_re.sub(lambda m: mapping[m.group(1)] + ".kt", text)
        else:
            text = code_re.sub(lambda m: mapping[m.group(1)], text)
        out.append(text)
    return "".join(out)


tracked = set(subprocess.check_output(["git", "ls-files", "-z"]).decode("utf-8", "surrogateescape").split("\0"))


def is_kt(p):
    return p.endswith(".kt") or p.endswith(".kts")


files = []
if not args:
    files = sorted(p for p in tracked if is_kt(p) and os.path.isfile(p))
else:
    seen = set()
    for a in args:
        if os.path.isfile(a):
            cands = [a]
        else:
            cands = []
            for d, dirs, fs in os.walk(a):
                dirs[:] = [x for x in dirs if x not in ("build", ".git")]
                cands += [os.path.join(d, f) for f in fs]
        for c in cands:
            rel = os.path.relpath(c, root)
            if is_kt(rel) and rel not in seen:
                seen.add(rel); files.append(rel)
    files.sort()

# Plan renames and check clashes before touching anything.
renames = []
for p in files:
    d, base = os.path.split(p)
    stem, ext = (base[:-3], ".kt") if base.endswith(".kt") else (base[:-4], ".kts")
    if stem in mapping:
        renames.append((p, os.path.join(d, mapping[stem] + ext)))
clashes = [(a, b) for a, b in renames if os.path.exists(b)]
if clashes:
    for a, b in clashes:
        print("error: cannot rename %s: target %s already exists" % (a, b), file=sys.stderr)
    sys.exit(1)

rewritten = 0
for p in files:
    with open(p, "rb") as f:
        raw = f.read()
    src = raw.decode("utf-8", "surrogateescape")
    new = rewrite(src)
    if new != src:
        with open(p, "wb") as f:
            f.write(new.encode("utf-8", "surrogateescape"))
        rewritten += 1

for a, b in renames:
    if a in tracked:
        subprocess.check_call(["git", "mv", "--", a, b])
    else:
        os.rename(a, b)

print("host rename: %d file(s) rewritten, %d file(s) renamed" % (rewritten, len(renames)))
PY
