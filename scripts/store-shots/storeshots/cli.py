"""`storeshots` command line. Subcommands are added by the modules that implement them."""
from __future__ import annotations

import argparse
import sys


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="storeshots", description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    from . import pipeline
    pipeline.register(sub)
    from . import capture
    capture.register(sub)
    from . import modules_fetch
    modules_fetch.register(sub)
    from . import modules_table
    modules_table.register(sub)
    from . import contact, fastlane
    contact.register(sub)
    fastlane.register(sub)
    from . import record
    record.register(sub)
    return parser


def main(argv: list[str] | None = None) -> int:
    from .errors import ShotError
    args = build_parser().parse_args(argv)
    try:
        return args.handler(args)
    except ShotError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
