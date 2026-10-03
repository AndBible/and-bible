"""Build andbible.org into website/_site/.

    uv run python -m sitegen.build [--content DIR] [--out DIR]

Each stage is one call in `build()`, in order. The output directory is rebuilt
from scratch so that removed content disappears from the next deploy.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

from sitegen import paths


def build(content: Path, out: Path) -> None:
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    (out / "CNAME").write_text("andbible.org\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--content", type=Path, default=paths.CONTENT)
    parser.add_argument("--out", type=Path, default=paths.SITE)
    args = parser.parse_args(argv)
    try:
        build(args.content, args.out)
    except ValueError as exc:
        print(f"site build failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
