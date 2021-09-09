TMP:=$(shell mktemp -d)

# Pushing source files to transifex:
# tx push -s -r andbible.play-store-main-description
# tx push -s -r andbible.and-bible-stringsxml
# tx push -s -r andbible.bibleview-js

tx-pull:
	tx pull --force --all
	# Download language corrections to english (en_GB in transifex, mapped to en via transifex config)
	tx pull --lang en_GB --force --minimum-perc 1 -r andbible.and-bible-stringsxml
	tx pull --lang en_GB --force --minimum-perc 1 -r andbible.bibleview-js
	python3 play/compile_description.py

fastlane-supply:
	# Remove languages unsupported by Google Play
	mv fastlane/metadata/android/eo $(TMP)/
	mv fastlane/metadata/android/uz $(TMP)/
	fastlane supply || true
	mv $(TMP)/* fastlane/metadata/android/
	rmdir $(TMP)

test:
	ls $(TMP)
	echo $(TMP)
	echo $(TMP)
	ls $(TMP)
