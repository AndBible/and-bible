#!/usr/bin/env python3
"""Package the original local SWORD KJV test module for Files bootstrap."""
import argparse
import hashlib
from pathlib import Path
import sys
import zipfile


ENTRIES = [
    "mods.d/kjv.conf",
    "modules/texts/ztext/kjv/nt.bzs",
    "modules/texts/ztext/kjv/nt.bzv",
    "modules/texts/ztext/kjv/nt.bzz",
    "modules/texts/ztext/kjv/ot.bzs",
    "modules/texts/ztext/kjv/ot.bzv",
    "modules/texts/ztext/kjv/ot.bzz",
    "modules/texts/ztext/kjv/kjvpref.rtf",
]


def parse_args():
    parser = argparse.ArgumentParser(
        description="Package existing real KJV module files without fetching or modifying them."
    )
    parser.add_argument("--sword-dir", required=True, type=Path, help="Existing SWORD module root (for example ~/.sword)")
    parser.add_argument("--output", required=True, type=Path, help="New ZIP archive path; existing files are refused")
    return parser.parse_args()


def load_payloads(source):
    if source.is_symlink() or not source.is_dir():
        raise ValueError("SWORD directory must be an existing non-symlink directory")
    config = source / ENTRIES[0]
    if config.is_symlink() or not config.is_file():
        raise ValueError("Expected regular module file: " + ENTRIES[0])
    try:
        metadata = config.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        raise ValueError("Unable to read KJV metadata: " + str(exc)) from exc
    sections = {}
    current_section = None
    for raw_line in metadata.splitlines():
        line = raw_line.strip()
        if line.startswith("[") and line.endswith("]"):
            current_section = line[1:-1]
            sections.setdefault(current_section, {})
        elif current_section and "=" in line and not line.startswith(";"):
            key, value = line.split("=", 1)
            sections[current_section][key.strip()] = value.strip()
    if sections.get("KJV", {}).get("DataPath") != "./modules/texts/ztext/kjv/":
        raise ValueError("Unexpected KJV metadata or DataPath")

    payloads = {}
    for name in ENTRIES:
        path = source / name
        if path.is_symlink() or not path.is_file():
            raise ValueError("Expected regular non-symlink module file: " + name)
        try:
            payloads[name] = path.read_bytes()
        except OSError as exc:
            raise ValueError("Unable to read module file " + name + ": " + str(exc)) from exc
    return payloads


def write_archive(output, payloads):
    if output.exists() or output.is_symlink():
        raise ValueError("Output already exists; refusing to overwrite: " + str(output))
    if not output.parent.is_dir():
        raise ValueError("Output parent directory must already exist: " + str(output.parent))
    try:
        with output.open("xb") as handle:
            with zipfile.ZipFile(handle, "w", compression=zipfile.ZIP_STORED) as archive:
                for name in ENTRIES:
                    info = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
                    info.create_system = 3
                    info.external_attr = 0o100644 << 16
                    archive.writestr(info, payloads[name])
    except FileExistsError:
        raise ValueError("Output already exists; refusing to overwrite: " + str(output)) from None


def main():
    args = parse_args()
    try:
        payloads = load_payloads(args.sword_dir)
        write_archive(args.output, payloads)
        with zipfile.ZipFile(args.output) as archive:
            if archive.namelist() != ENTRIES:
                raise ValueError("Created archive has unexpected entries")
            for name in ENTRIES:
                if archive.read(name) != payloads[name]:
                    raise ValueError("Created archive failed byte validation: " + name)
        digest = hashlib.sha256(args.output.read_bytes()).hexdigest()
        print("Created " + str(args.output) + " (sha256 " + digest + ")")
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        print("package-backup-test-kjv: " + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
