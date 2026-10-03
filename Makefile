TMP:=$(shell mktemp -d)

increment-version:
	./scripts/increment-version.sh
increment-test-version:
	./scripts/increment-version.sh --build

tx-push-sources:
	tx push -s -r andbible.play-store-main-description
	tx push -s -r andbible.and-bible-stringsxml
	tx push -s -r andbible.bibleview-js


tx-push-all:
	tx push -s -t -r andbible.play-store-main-description
	tx push -s -t -r andbible.and-bible-stringsxml
	tx push -s -t -r andbible.bibleview-js

tx-pull:
	tx pull --force --all
	cp app/src/main/res/values-zh/strings.xml app/src/main/res/values-zh-rTW/strings.xml
	# Download language corrections to english (en_GB in transifex, mapped to en via transifex config)
	tx pull -l en_GB --force --minimum-perc 1 -r andbible.and-bible-stringsxml
	tx pull -l en_GB --force --minimum-perc 1 -r andbible.bibleview-js
	tx pull -l en_GB --force --minimum-perc 1 -r andbible.play-store-main-description
	rm play/description-translations/sr@latin.yml
	python3 app/bibleview-js/src/lang/check.py
	python3 play/compile_description.py

fastlane-supply:
	# Remove languages unsupported by Google Play
	mv fastlane/metadata/android/eo $(TMP)/
	mv fastlane/metadata/android/yue $(TMP)/
	mv fastlane/metadata/android/my-MM $(TMP)/  # description too long, update manually
	#mv fastlane/metadata/android/uz $(TMP)/
	fastlane supply || true
	mv $(TMP)/* fastlane/metadata/android/
	rmdir $(TMP)

test:
	ls $(TMP)
	echo $(TMP)
	echo $(TMP)
	ls $(TMP)

instrumented-tests:
	./gradlew emulatorStandardGoogleplayDebugAndroidTest

install-debug:
	@echo "Assembling standard github debug APK..."
	./gradlew assembleStandardGithubDebug
	@echo "Installing APK to connected device..."
	adb install -r app/build/outputs/apk/standardGithub/debug/app-standard-github-debug.apk
	@echo "✓ Installed"

install-prod:
	@echo "Assembling standard github release APK (signing via keystore.properties.gpg)..."
	./gradlew assembleStandardGithubRelease
	@echo "Installing APK to connected device..."
	adb install -r app/build/outputs/apk/standardGithub/release/app-standard-github-release.apk
	@echo "✓ Installed"

fdroid-release:
	@VERSION_NAME=$$(grep -o 'android:versionName="[^"]*"' app/src/main/AndroidManifest.xml | grep -o '"[^"]*"' | tr -d '"'); \
	TAG="v$$VERSION_NAME-fdroid"; \
	echo "Creating F-Droid tag: $$TAG"; \
	git tag -s "$$TAG" -m "F-Droid release $$VERSION_NAME"; \
	echo "Pushing tag to GitHub..."; \
	git push origin "$$TAG"; \
	echo "Done: $$TAG"

bundle:
	@echo "Building Google Play AAB bundle (signing via keystore.properties.gpg)..."
	./gradlew bundleStandardGoogleplayRelease
	@echo "✓ AAB: app/build/outputs/bundle/standardGoogleplayRelease/app-standard-googleplay-release.aab"

accrescent:
	@echo "Building Accrescent APK set (signing via keystore.properties.gpg)..."
	./gradlew buildApksStandardAccrescentRelease
	@mkdir -p app/standardAccrescent/release
	@cp app/build/outputs/apkset/standardAccrescentRelease/app-standardAccrescentRelease.apks app/standardAccrescent/release/
	@echo "✓ APK set: app/standardAccrescent/release/app-standardAccrescentRelease.apks"

accrescent-debug:
	@echo "Building Accrescent Debug APK set (signing via keystore.properties.gpg)..."
	./gradlew buildApksStandardAccrescentDebug
	@mkdir -p app/standardAccrescent/debug
	@cp app/build/outputs/apkset/standardAccrescentDebug/app-standardAccrescentDebug.apks app/standardAccrescent/debug/
	@echo "✓ APK set: app/standardAccrescent/debug/app-standardAccrescentDebug.apks"

.PHONY: increment-version increment-test-version tx-push tx-pull fastlane-supply test instrumented-tests install-debug install-prod fdroid-release bundle accrescent accrescent-debug
