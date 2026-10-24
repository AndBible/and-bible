#!/bin/bash
# 93-fastlane — fastlane for the store release pipeline (Play `supply`, App Store Connect
# `pilot`/`deliver`/Spaceship), see specs/2026-10-24-i4-release-design.md §5.4.
#
# Ruby, ruby-dev and build-essential come from golden.extra_apt_packages; rubygems.org and
# index.rubygems.org are in egress_allow. Harmless without keys: the store credentials come only from
# the optional mount `release-secrets`, and the store API hosts are opened per release container
# (`jailbee net egress add`), not here.
# Versions verified by the I4a spike (2026-10-24, user GEM_HOME install): ruby 3.3.8, fastlane 2.241.1.
# Env: (none) — runs as root in the golden-build container.
# Installs: fastlane into the system gem dir, /usr/local/bin/fastlane
set -euo pipefail

FASTLANE_VERSION="2.241.1"

if ! command -v gem >/dev/null 2>&1; then
  echo "93-fastlane: gem not found (ruby missing from golden.extra_apt_packages?), skipping" >&2
  exit 0
fi

gem install fastlane -v "${FASTLANE_VERSION}" --no-document --bindir /usr/local/bin
FASTLANE_SKIP_UPDATE_CHECK=1 FASTLANE_OPT_OUT_USAGE=1 LC_ALL=C.UTF-8 /usr/local/bin/fastlane --version >/dev/null
echo "93-fastlane: installed fastlane ${FASTLANE_VERSION} ($(ruby --version))"
