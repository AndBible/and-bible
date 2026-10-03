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

# Push the current branch, pushing our own submodules (goldens, superpowers) first so the
# gitlinks never point at unpublished commits. Each submodule pushes the commit the committed
# gitlink names (not its checkout, which can lag behind) to the superproject's branch name,
# fast-forward only. jsword is an upstream fork, pushed by hand; --recurse-submodules=check
# refuses the final push if any gitlink (jsword included) is unpushed.
PUSH_SUBMODULES := app/src/test/roborazzi docs/superpowers

# andbible.org (website/): build into website/_site, then check it. CI runs both.
site:
	cd website && uv run python -m sitegen.build

site-check:
	cd website && uv run python -m sitegen.check && uv run pytest

# Preview the built site at http://localhost:8000/
site-serve:
	cd website/_site && python3 -m http.server 8000

push:
	@set -e; \
	branch=$$(git symbolic-ref --short HEAD) || { echo "push: detached HEAD" >&2; exit 1; }; \
	for sm in $(PUSH_SUBMODULES); do \
		if [ ! -e "$$sm/.git" ]; then echo "push: $$sm not checked out, skipping"; continue; fi; \
		sha=$$(git rev-parse "HEAD:$$sm"); \
		[ "$$(git -C "$$sm" rev-parse HEAD)" = "$$sha" ] || \
			echo "push: note: $$sm checkout differs from the committed gitlink; pushing the gitlink"; \
		echo "push: $$sm ($$(git -C "$$sm" rev-parse --short "$$sha") -> $$branch)"; \
		git -C "$$sm" push origin "$$sha:refs/heads/$$branch"; \
	done; \
	echo "push: and-bible ($$branch)"; \
	git push --recurse-submodules=check -u origin "$$branch"

.PHONY: increment-version increment-test-version tx-push tx-pull fastlane-supply test instrumented-tests install-debug install-prod fdroid-release bundle accrescent accrescent-debug site site-check site-serve push
