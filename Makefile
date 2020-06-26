tx-pull:
	tx pull --force --all
	# Download language corrections to english (en_GB in transifex, mapped to en via transifex config)
	tx pull --lang en_GB --force --minimum-perc 1

fastlane-supply:
	# Remove languages unsupported by Google Play
	mkdir tmp
	mv fastlane/metadata/android/{eo,uz} tmp
	fastlane supply
	mv tmp/* fastlane/metadata/android/
	rmdir tmp
