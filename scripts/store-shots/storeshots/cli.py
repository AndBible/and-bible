"""`storeshots` command line. Subcommands are added by the modules that implement them."""
from __future__ import annotations

import argparse
import sys


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="storeshots", description=__doc__)
    parser.add_subparsers(dest="command", required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    handler = getattr(args, "handler", None)
    return handler(args) if handler else 2


if __name__ == "__main__":
    sys.exit(main())
