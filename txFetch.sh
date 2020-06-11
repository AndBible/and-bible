#!/bin/bash
tx pull --force --all

# Download language corrections to english (en_GB in transifex, mapped to en via transifex config)
tx pull --lang en_GB --force --minimum-perc 1

# Remove languages unsupported by Google Play
rm -r fastlane/metadata/android/{eo,uz}

