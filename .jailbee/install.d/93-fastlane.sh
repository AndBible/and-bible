#!/bin/bash
# 93-fastlane — Ruby and fastlane for the store release pipeline (Play `supply`, App Store Connect
# `pilot`/`deliver`/Spaceship), see specs/2026-10-24-i4-release-design.md §5.4.
#
# Harmless without keys: the store credentials come only from the optional mount `release-secrets`,
# and the store API hosts are opened per release container (`jailbee net egress add`), not here.
# Version verified by the I4a spike (2026-10-24): ruby 3.3.8 (Ubuntu apt), fastlane 2.241.1.
# Env: (none) — runs as root in the golden-build container.
# Installs: ruby, ruby-dev, build-essential (apt); fastlane into the system gem dir, /usr/local/bin/fastlane
set -euo pipefail

FASTLANE_VERSION="2.241.1"

export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q --no-install-recommends ruby ruby-dev build-essential
gem install fastlane -v "${FASTLANE_VERSION}" --no-document --bindir /usr/local/bin
fastlane --version >/dev/null
echo "93-fastlane: installed fastlane ${FASTLANE_VERSION} ($(ruby --version))"
