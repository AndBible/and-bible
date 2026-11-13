TMP:=$(shell mktemp -d)

# Version code step; e.g. `make increment-test-version STEP=100` leaves a gap
# of version codes free for current-stable bug-fix releases.
STEP ?= 1
increment-version:
	./scripts/increment-version.sh --step $(STEP)
increment-beta-version:
	./scripts/increment-version.sh --beta --step $(STEP)
increment-test-version:
	./scripts/increment-version.sh --build --step $(STEP)

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
	./gradlew -Pandbible.sign=true assembleStandardGithubRelease
	@echo "Installing APK to connected device..."
	adb install -r app/build/outputs/apk/standardGithub/release/app-standard-github-release.apk
	@echo "✓ Installed"

fdroid-release:
	@VERSION_NAME=$$(bash -c '. scripts/lib-version.sh; version_name'); \
	if [ -z "$$VERSION_NAME" ]; then echo "fdroid-release: could not read the version from version.properties" >&2; exit 1; fi; \
	TAG="v$$VERSION_NAME-fdroid"; \
	echo "Creating F-Droid tag: $$TAG"; \
	git tag -a "$$TAG" -m "F-Droid release $$VERSION_NAME"; \
	echo "Pushing tag to GitHub..."; \
	git push origin "$$TAG"; \
	echo "Done: $$TAG"

bundle:
	@echo "Building Google Play AAB bundle (signing via keystore.properties.gpg)..."
	./gradlew -Pandbible.sign=true bundleStandardGoogleplayRelease
	@echo "✓ AAB: app/build/outputs/bundle/standardGoogleplayRelease/app-standard-googleplay-release.aab"

accrescent:
	@echo "Building Accrescent APK set (signing via keystore.properties.gpg)..."
	./gradlew -Pandbible.sign=true buildApksStandardAccrescentRelease
	@mkdir -p app/standardAccrescent/release
	@cp app/build/outputs/apkset/standardAccrescentRelease/app-standardAccrescentRelease.apks app/standardAccrescent/release/
	@echo "✓ APK set: app/standardAccrescent/release/app-standardAccrescentRelease.apks"

accrescent-debug:
	@echo "Building Accrescent Debug APK set (signing via keystore.properties.gpg)..."
	./gradlew -Pandbible.sign=true buildApksStandardAccrescentDebug
	@mkdir -p app/standardAccrescent/debug
	@cp app/build/outputs/apkset/standardAccrescentDebug/app-standardAccrescentDebug.apks app/standardAccrescent/debug/
	@echo "✓ APK set: app/standardAccrescent/debug/app-standardAccrescentDebug.apks"

# make push [DRY_RUN=1]: push the current branch, publishing our own submodules (jsword, goldens, superpowers, media,
# kdom, store screenshots) first so no pushed gitlink points at an unpublished commit. Each submodule pushes the commit
# HEAD's gitlink names to the superproject's branch name (fast-forward only), plus any gitlink of an intermediate
# pushed commit that no origin branch contains (to gitlink/<sha>). Details: scripts/push.sh (tested by scripts/test-push.sh).
PUSH_SUBMODULES := jsword app/src/test/roborazzi docs/superpowers website/media kdom store-screenshots

# andbible.org (website/): build into website/_site, then check it. CI runs both.
site:
	cd website && uv run python -m sitegen.build

site-check:
	cd website && uv run python -m sitegen.check && uv run pytest

# Fetch missing local YouTube thumbnails into website/media/videos/. Pass options with THUMBS_ARGS:
#   make site-thumbs THUMBS_ARGS="--refresh <id-or-url> ..."   re-download after changing a thumbnail on YouTube
#   make site-thumbs THUMBS_ARGS="--all"                       re-download every video
#   make site-thumbs THUMBS_ARGS="--all --dry-run"             only list what would be fetched
site-thumbs:
	cd website && uv run python -m sitegen.thumbnails $(THUMBS_ARGS)

# Preview the built site at http://localhost:8000/
site-serve:
	cd website/_site && python3 -m http.server 8000

push:
	@scripts/push.sh $(if $(DRY_RUN),--dry-run,) $(PUSH_SUBMODULES)

# Release pipeline (I4c-1)
# One-touch internal release of the clean HEAD: Google Play internal + TestFlight internal in parallel, one YubiKey touch
# (scripts/release/release-internal.sh, skill .claude/skills/release).
#   make release-internal [PLATFORM=android|ios|both] [DEST=upload|export] [DRY_RUN=1]
# DEST=export builds and checks both artifacts but uploads nothing. make testflight = PLATFORM=ios.
release-internal:
	@case "$(PLATFORM)" in ""|android|ios|both) ;; *) echo "release-internal: PLATFORM must be android, ios or both (got '$(PLATFORM)')" >&2; exit 1;; esac
	@case "$(DEST)" in ""|upload|export) ;; *) echo "release-internal: DEST must be empty, upload or export (got '$(DEST)')" >&2; exit 1;; esac
	@scripts/release/release-internal.sh --platform $(or $(PLATFORM),both) $(if $(filter export,$(DEST)),--export-only,) $(if $(DRY_RUN),--dry-run,)

testflight:
	@case "$(DEST)" in ""|upload|export) ;; *) echo "testflight: DEST must be empty, upload or export (got '$(DEST)')" >&2; exit 1;; esac
	@scripts/release/release-internal.sh --platform ios $(if $(filter export,$(DEST)),--export-only,) $(if $(DRY_RUN),--dry-run,)

.PHONY: increment-version increment-beta-version increment-test-version tx-push tx-pull fastlane-supply test instrumented-tests install-debug install-prod fdroid-release bundle accrescent accrescent-debug site site-check site-thumbs site-serve push testflight release-internal
