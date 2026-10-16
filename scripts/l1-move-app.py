"""Helper for l1-move-readiness.sh: counts imports of :app symbols that are neither in a domain nor allow-listed.

Usage: python3 -I l1-move-app.py ROOT [--lines] < domain-file-list
Reads the domain's file paths (absolute) on stdin; prints the count, or with --lines the offending
"file:line: import" rows.
"""
import os
import re
import sys

root = sys.argv[1]
show = "--lines" in sys.argv[2:]
src = os.path.join(root, "app/src/main/java/net/bible")


def rows(path):
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            return [r.rstrip("\n").split("\t") for r in fh if r.strip() and not r.startswith("@")]
    except OSError:
        return []


domains = [(r[1], r[0]) for r in rows(os.path.join(root, "scripts/l1-readiness-domains.txt")) if len(r) >= 2]
platform = {r[0] for r in rows(os.path.join(root, "scripts/l1-readiness-platform.txt"))}
allow = [r[0] for r in rows(os.path.join(root, "scripts/l1-move-allow.txt"))]


def in_prefix(rel, p):
    return rel == p or rel.startswith(p + "/")


def is_domain(rel):
    return rel not in platform and any(in_prefix(rel, p) for p, _ in domains)


def is_allowed(rel):
    return any(in_prefix(rel, p) for p in allow)


PKG = re.compile(r"^package\s+([\w.]+)")
# 'fun X.name' / 'fun <T> X.name' / 'val X.name': the declared name is the last identifier before ( or : or =.
EXT = re.compile(r"^(?:@\w+(?:\([^)]*\))?\s+)*(?:\w+\s+)*(?:fun|val|var)\s+(?:<[^>]*>\s*)?[\w<>?,. ]+?\.(\w+)(?:\s*[(:=<{]|\s+get\b)")
PLAIN = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s+)*"
    r"(?:(?:public|fun|internal|private|protected|abstract|open|sealed|data|enum|annotation|inline|value|"
    r"expect|actual|const|lateinit|suspend|tailrec|operator|infix|external)\s+)*"
    r"(?:class|interface|object|typealias|val|var|fun)\s+(?:<[^>]*>\s*)?(\w+)"
)
IMPORT = re.compile(r"^import\s+(net\.bible\.[\w.]+?)(?:\s+as\s+\w+)?\s*$")

app_index = {}     # fqn -> True, files that count (non-domain, non-allowed)
domain_index = set()  # fqn declared in a domain/allowed file; never counted
for dp, _, fns in os.walk(src):
    for fn in fns:
        if not fn.endswith((".kt", ".java")):
            continue
        path = os.path.join(dp, fn)
        rel = os.path.relpath(path, src)
        counts = not is_domain(rel) and not is_allowed(rel)
        pkg = None
        names = set()
        with open(path, encoding="utf-8", errors="replace") as fh:
            for line in fh:
                m = PKG.match(line)
                if m:
                    pkg = m.group(1)
                    continue
                if not line or line[0] in " \t/*}\n":
                    continue
                m = EXT.match(line) or PLAIN.match(line)
                if m:
                    names.add(m.group(1))
        if fn.endswith(".java"):
            names.add(fn[:-5])
        if pkg:
            names.add(fn.rsplit(".", 1)[0])  # file-level facade / single-class file name
            for n in names:
                if counts:
                    app_index[pkg + "." + n] = True
                else:
                    domain_index.add(pkg + "." + n)


def resolves(fqn):
    parts = fqn.split(".")
    while len(parts) > 2:
        cand = ".".join(parts)
        if cand in domain_index:
            return False
        if cand in app_index:
            return True
        parts.pop()
    return False


n = 0
for path in (l.strip() for l in sys.stdin if l.strip()):
    with open(path, encoding="utf-8", errors="replace") as fh:
        for i, line in enumerate(fh, 1):
            clean = re.sub(r"/\*.*?\*/|//.*$", "", line).strip().rstrip(";").strip()
            m = IMPORT.match(clean)
            if m and resolves(m.group(1)):
                n += 1
                if show:
                    print(f"{path}:{i}: {line.strip()}")
if not show:
    print(n)
