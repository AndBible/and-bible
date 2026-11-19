#!/usr/bin/env python3
"""Measure how much of a release build R8 obfuscated, from its mapping.txt (#4160; docs/r8-obfuscation.md).

Usage: scripts/r8-obfuscation-share.py <mapping.txt> [--apk APK] [--by-prefix PREFIX ...]

Counts the classes, methods and fields that the mapping lists (classes R8 removed excepted) and how many of them got a new name. Prints the shares
overall and for each given package prefix (default: net.bible., org.crosswire., and everything else). With --apk, also
counts the classes actually defined in the APK's DEX files and how many of them carry an obfuscated name (the mapping
also lists classes R8 removed or merged). Play Console's "Obfuscation" percentage must stay at or above 25 %.
Constructors are not counted; a member with several line ranges counts once.
"""
import re
import struct
import sys
import zipfile

CLASS_RE = re.compile(r"^(\S+) -> (\S+):$")
# "    [12:34:]type name(args)[:line[:line]] -> newName" for methods; "    type name -> newName" for fields.
MEMBER_RE = re.compile(r"^\s+(?:\d+:\d+:)?(\S+) ([^\s(]+)(\(.*?\))?(?::\d+(?::\d+)?)? -> (\S+)$")


def measure(path, prefixes):
    buckets = {p: [0, 0, 0, 0, 0, 0] for p in prefixes + ["(other)", "(all)"]}

    def bucket_of(cls):
        for p in prefixes:
            if cls.startswith(p):
                return p
        return "(other)"

    cur = None
    seen = set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            m = CLASS_RE.match(line)
            if m:
                orig, new = m.groups()
                if new.startswith("R8$$REMOVED$$"):  # removed from the output, not renamed
                    cur = None
                    continue
                cur = bucket_of(orig)
                seen = set()
                for b in (cur, "(all)"):
                    buckets[b][0] += 1
                    buckets[b][1] += orig != new
                continue
            m = MEMBER_RE.match(line)
            if m and cur is not None:
                _type, name, args, new = m.groups()
                if "." in name:  # inlined frame from another class, not a member of this one
                    continue
                key = (name, args, new)
                if key in seen:  # one member, several line ranges
                    continue
                seen.add(key)
                idx = 2 if args is not None else 4
                if name in ("<init>", "<clinit>"):
                    continue
                for b in (cur, "(all)"):
                    buckets[b][idx] += 1
                    buckets[b][idx + 1] += name != new
    return buckets


def _uleb(d, o):
    r = s = 0
    while True:
        b = d[o]
        o += 1
        r |= (b & 0x7F) << s
        s += 7
        if b < 0x80:
            return r, o


def dex_class_names(dex):
    """The class names (dotted) defined in one DEX file, read from its header's string/type/class_def tables."""
    str_size, str_off = struct.unpack_from("<II", dex, 0x38)
    type_size, type_off = struct.unpack_from("<II", dex, 0x40)
    class_size, class_off = struct.unpack_from("<II", dex, 0x60)

    def string(i):
        data_off = struct.unpack_from("<I", dex, str_off + 4 * i)[0]
        _, o = _uleb(dex, data_off)
        return dex[o:dex.index(b"\0", o)].decode("utf-8", "replace")

    for i in range(class_size):
        type_idx = struct.unpack_from("<I", dex, class_off + 32 * i)[0]
        descriptor = string(struct.unpack_from("<I", dex, type_off + 4 * type_idx)[0])
        yield descriptor[1:-1].replace("/", ".")


def measure_apk(apk, mapping, prefixes):
    """Per bucket: [classes in the DEX, of which renamed]; a DEX class missing from the mapping counts as not renamed."""
    original = {}
    with open(mapping, encoding="utf-8") as f:
        for line in f:
            m = CLASS_RE.match(line)
            if m:
                original[m.group(2)] = m.group(1)
    buckets = {p: [0, 0] for p in prefixes + ["(other)", "(all)"]}
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if not re.fullmatch(r"classes\d*\.dex", name):
                continue
            for cls in dex_class_names(z.read(name)):
                orig = original.get(cls, cls)
                b = next((p for p in prefixes if orig.startswith(p)), "(other)")
                for k in (b, "(all)"):
                    buckets[k][0] += 1
                    buckets[k][1] += orig != cls
    return buckets


def pct(a, b):
    return f"{100.0 * a / b:5.1f} %" if b else "    - "


def main(argv):
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    prefixes = ["net.bible.", "org.crosswire."]
    if "--by-prefix" in argv:
        rest = argv[argv.index("--by-prefix") + 1:]
        prefixes = rest[:next((i for i, a in enumerate(rest) if a.startswith("--")), len(rest))]
    apk = argv[argv.index("--apk") + 1] if "--apk" in argv else None
    b = measure(argv[1], prefixes)
    print("Mapping (every class and member R8 kept, renamed or merged):")
    print(f"{'scope':<16} {'classes':>8} {'renamed':>8} {'share':>8}   {'methods':>8} {'renamed':>8} {'share':>8}"
          f"   {'fields':>8} {'renamed':>8} {'share':>8}")
    for name, (c, cr, m, mr, fl, fr) in b.items():
        print(f"{name:<16} {c:>8} {cr:>8} {pct(cr, c):>8}   {m:>8} {mr:>8} {pct(mr, m):>8}   {fl:>8} {fr:>8} {pct(fr, fl):>8}")
    if apk:
        print(f"\nDEX of {apk} (classes it defines):")
        print(f"{'scope':<16} {'classes':>8} {'renamed':>8} {'share':>8}")
        for name, (c, cr) in measure_apk(apk, argv[1], prefixes).items():
            print(f"{name:<16} {c:>8} {cr:>8} {pct(cr, c):>8}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
