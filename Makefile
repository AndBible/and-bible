TMP:=$(shell mktemp -d)

tx-pull:
	tx pull --force --all
	# Download language corrections to english (en_GB in transifex, mapped to en via transifex config)
	tx pull --lang en_GB --force --minimum-perc 1
	rm -r fastlane/metadata/android/ar

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
