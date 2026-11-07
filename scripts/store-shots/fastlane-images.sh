#!/usr/bin/env bash
# Copies store-screenshots/android/<play>/{phone,seven-inch,ten-inch} into fastlane/metadata/android/<play>/images
# before `supply`. `--check` only prints per-locale counts and exits 1 on a partial locale.
# Options: --check, --src DIR (store-screenshots), --dest DIR (fastlane/metadata/android).
exec "$(dirname "$0")/run.sh" fastlane "$@"
