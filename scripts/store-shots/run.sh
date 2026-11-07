#!/usr/bin/env bash
# Runs the storeshots CLI from this directory: ./run.sh frame --lang en --platform android
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -f ../store-screenshots/style/fonts/NotoSans-Bold.ttf ] && [ -x ../store-screenshots/style/fetch-fonts.sh ]; then
  ../store-screenshots/style/fetch-fonts.sh
fi
uv run --no-project --with pillow python -c "import sys; from PIL import features; sys.exit(0 if features.check('raqm') else 'Pillow lacks libraqm: captions in Arabic/Indic scripts cannot be shaped')"
exec uv run --no-project --with pillow --with pyyaml --with fonttools python -m storeshots.cli "$@"
