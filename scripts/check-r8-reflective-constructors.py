#!/usr/bin/env python3
"""Check that R8 kept the no-argument constructors that libraries call by reflection (#4175; docs/r8-obfuscation.md).

Usage: scripts/check-r8-reflective-constructors.py <mapping.txt[.gz]> (--apk APK | --dex-dir DIR) [--base CLASS ...]

Some libraries create instances by class name with `Class.newInstance()` / `getDeclaredConstructor().newInstance()`:
Room (`<Database>_Impl`, Room 2 and Room 3), WorkManager (`InputMerger` subclasses named in a WorkSpec) and
androidx.startup (`Initializer`s named in the manifest). R8 full mode (AGP 8 default) does not keep the default
constructor of a class kept by a member-less `-keep class * extends Base` rule. With no direct `new` call it then
removes the constructor and makes the class abstract, so the reflective call throws `InstantiationException`. Build
6.0.1279 crash-looped at startup this way: WorkManager 2.7.1 (pulled in by Glance) brings Room 2.2.5, whose consumer
rule has no `{ <init>(); }`, so `WorkDatabase_Impl` could not be created.

For every base class (default: the list in BASES), every leaf subclass in the DEX (a class that descends from the
base and has no subclass of its own in the DEX) must be concrete and have a no-argument constructor. Descent is read
from the DEX superclass and interface chain. R8 may merge the base class into its only subclass, so the base's name
disappears; a class whose mapping entry lists fields or methods that originally belonged to the base counts as the
base. The mapping also translates obfuscated names back for the report.

Prints each base with the number of leaves it checked. Exit 0 when all pass, 1 on a violation, 2 on usage errors.
"""
import gzip
import re
import struct
import sys
import zipfile
from pathlib import Path

BASES = [
    "androidx.room.RoomDatabase",     # Room 2 (WorkManager's WorkDatabase): Room.getGeneratedImplementation
    "androidx.room3.RoomDatabase",    # Room 3 (AndBible's databases)
    "androidx.work.InputMerger",      # InputMerger.fromClassName / InputMergerFactory
    "androidx.startup.Initializer",   # AppInitializer.doInitialize
]

ACC_INTERFACE = 0x200
ACC_ABSTRACT = 0x400
NO_INDEX = 0xFFFFFFFF

CLASS_RE = re.compile(r"^(\S+) -> (\S+):$")
# "    [1:5:]type [holder.]name[(args)][:line[:line]] -> new"
MEMBER_RE = re.compile(r"^\s+(\d+:\d+:)?\S+ ([^\s(]+)(\(.*?\))?(?::\d+(?::\d+)?)? -> \S+$")


def open_text(path):
    return gzip.open(path, "rt", encoding="utf-8") if str(path).endswith(".gz") else open(path, encoding="utf-8")


def read_mapping(path):
    """Return (obfuscated -> original class name, obfuscated -> set of original classes merged into it).

    A member line naming another holder (`Type a.b.Base.field -> x`) is either an inlined frame or a member that moved
    in from a merged class. Inlined frames come in runs of lines with the same `start:end:` range, the outermost frame
    (the method that really exists) last; so only the last line of such a run, a range-less method or a field counts.
    """
    original, absorbed = {}, {}
    cur_obf = cur_orig = None
    pending = None  # (range, holder) of the previous method line

    def flush():
        if pending and pending[1] and cur_obf is not None:
            absorbed[cur_obf].add(pending[1])

    with open_text(path) as f:
        for line in f:
            if line.lstrip().startswith("#"):
                continue
            m = CLASS_RE.match(line)
            if m:
                flush()
                pending = None
                cur_orig, cur_obf = m.groups()
                original[cur_obf] = cur_orig
                absorbed.setdefault(cur_obf, set())
                continue
            m = MEMBER_RE.match(line)
            if not m or cur_obf is None:
                continue
            rng, name, args = m.groups()
            holder = name.rsplit(".", 1)[0] if "." in name else None
            if holder == cur_orig:
                holder = None
            if args is None:  # a field: never inlined
                if holder:
                    absorbed[cur_obf].add(holder)
                continue
            if pending and rng is not None and pending[0] == rng:
                pending = (rng, holder)  # same inline run: this line is further out
                continue
            flush()
            pending = (rng, holder)
        flush()
    return original, absorbed


def _uleb(d, o):
    r = s = 0
    while True:
        b = d[o]
        o += 1
        r |= (b & 0x7F) << s
        s += 7
        if b < 0x80:
            return r, o


def read_dex(dex, classes):
    """Add {name: (access_flags, superclass, [interfaces], has_no_arg_init)} for the classes one DEX file defines."""
    str_size, str_off = struct.unpack_from("<II", dex, 0x38)
    type_size, type_off = struct.unpack_from("<II", dex, 0x40)
    proto_size, proto_off = struct.unpack_from("<II", dex, 0x48)
    method_size, method_off = struct.unpack_from("<II", dex, 0x58)
    class_size, class_off = struct.unpack_from("<II", dex, 0x60)

    def string(i):
        data_off = struct.unpack_from("<I", dex, str_off + 4 * i)[0]
        _, o = _uleb(dex, data_off)
        return dex[o:dex.index(b"\0", o)].decode("utf-8", "replace")

    def type_name(i):
        return string(struct.unpack_from("<I", dex, type_off + 4 * i)[0])[1:-1].replace("/", ".")

    for i in range(class_size):
        (cls_idx, flags, super_idx, ifaces_off, _src, _ann, data_off, _static) = struct.unpack_from(
            "<8I", dex, class_off + 32 * i)
        name = type_name(cls_idx)
        superclass = type_name(super_idx) if super_idx != NO_INDEX else None
        interfaces = []
        if ifaces_off:
            n = struct.unpack_from("<I", dex, ifaces_off)[0]
            interfaces = [type_name(struct.unpack_from("<H", dex, ifaces_off + 4 + 2 * k)[0]) for k in range(n)]
        has_init = False
        if data_off:
            o = data_off
            sf, o = _uleb(dex, o)
            inf, o = _uleb(dex, o)
            direct, o = _uleb(dex, o)
            _virtual, o = _uleb(dex, o)
            for _ in range(2 * (sf + inf)):  # (field_idx_diff, access_flags) pairs
                _, o = _uleb(dex, o)
            midx = 0
            for _ in range(direct):
                diff, o = _uleb(dex, o)
                _acc, o = _uleb(dex, o)
                _code, o = _uleb(dex, o)
                midx += diff
                _cls, proto_idx, name_idx = struct.unpack_from("<HHI", dex, method_off + 8 * midx)
                params_off = struct.unpack_from("<I", dex, proto_off + 12 * proto_idx + 8)[0]
                if string(name_idx) == "<init>" and params_off == 0:
                    has_init = True
        classes[name] = (flags, superclass, interfaces, has_init)


def load_classes(apk=None, dex_dir=None):
    classes = {}
    if apk:
        with zipfile.ZipFile(apk) as z:
            for n in z.namelist():
                if re.fullmatch(r"classes\d*\.dex", n):
                    read_dex(z.read(n), classes)
    else:
        for p in sorted(Path(dex_dir).rglob("*.dex")):
            read_dex(p.read_bytes(), classes)
    return classes


def check(classes, original, absorbed, bases):
    """Return (report lines, violations)."""
    orig = lambda c: original.get(c, c)  # noqa: E731

    def stands_for(c, base):
        return orig(c) == base or base in absorbed.get(c, ())

    memo = {}

    def descends(c, base, seen=()):
        key = (c, base)
        if key in memo:
            return memo[key]
        if c in seen or c not in classes:
            return False
        _flags, sup, ifaces, _init = classes[c]
        result = False
        for parent in ([sup] if sup else []) + ifaces:
            if stands_for(parent, base) or descends(parent, base, seen + (c,)):
                result = True
                break
        memo[key] = result
        return result

    report, violations = [], []
    for base in bases:
        members = {c for c in classes if descends(c, base) or (stands_for(c, base) and orig(c) != base)}
        parents = set()
        for c in members:
            _f, sup, ifaces, _i = classes[c]
            parents.update([sup] if sup else [])
            parents.update(ifaces)
        leaves = sorted(c for c in members if c not in parents)
        bad = []
        for c in leaves:
            flags, _sup, _ifaces, has_init = classes[c]
            problems = []
            if flags & (ACC_ABSTRACT | ACC_INTERFACE):
                problems.append("abstract")
            if not has_init:
                problems.append("no no-argument constructor")
            if problems:
                bad.append(f"{orig(c)}" + (f" ({c})" if orig(c) != c else "") + ": " + ", ".join(problems))
        report.append(f"{base}: {len(leaves)} leaf classes checked" + (f", {len(bad)} broken" if bad else ""))
        violations += [f"{base} subclass {b}" for b in bad]
    return report, violations


def main(argv):
    args = argv[1:]
    mapping = apk = dex_dir = None
    bases = []
    while args:
        a = args.pop(0)
        if a == "--apk" and args:
            apk = args.pop(0)
        elif a == "--dex-dir" and args:
            dex_dir = args.pop(0)
        elif a == "--base" and args:
            bases.append(args.pop(0))
        elif not a.startswith("--") and mapping is None:
            mapping = a
        else:
            print(__doc__, file=sys.stderr)
            return 2
    if mapping is None or (apk is None) == (dex_dir is None):
        print(__doc__, file=sys.stderr)
        return 2
    original, absorbed = read_mapping(mapping)
    classes = load_classes(apk, dex_dir)
    if not classes:
        print(f"check-r8-reflective-constructors: no DEX classes found in {apk or dex_dir}", file=sys.stderr)
        return 2
    report, violations = check(classes, original, absorbed, bases or BASES)
    for line in report:
        print(line)
    if violations:
        print("\nR8 removed constructors that are called by reflection (the app crashes with InstantiationException):",
              file=sys.stderr)
        for v in violations:
            print("  " + v, file=sys.stderr)
        print("Add a keep rule with the constructor, e.g. `-keep class * extends <Base> { <init>(); }`, to "
              "app/proguard-rules.pro (docs/r8-obfuscation.md).", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
