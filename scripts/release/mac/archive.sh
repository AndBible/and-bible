#!/usr/bin/env bash
# Runs ON THE MAC from the synced tree root (scripts/mac-remote.sh run cds there), under macOS bash and BSD tools.
# Archives the Release configuration of iosApp, re-signs with the Release entitlements, exports an app-store-connect IPA,
# checks it, and (destination upload) uploads the same archive to App Store Connect / TestFlight.
# Usage: scripts/release/mac/archive.sh <ramdisk-volume> <key-id> <issuer-id> <team-id> <build> <version> <commit> [upload|export] [reuse]
#   reuse: skip the bibleview build, xcodegen, archive and re-sign, and export (and upload) the existing
#   build/release/AndBible-<build>.xcarchive of an earlier run; exit 3 before using the key when that archive is missing or
#   its app's CFBundleVersion/CFBundleShortVersionString are not <build>/<version> (the caller then archives afresh).
#   <version>/<commit> (name, 10-char short hash) are computed in the container and compared with the synced
#   iosApp/Config/Version.xcconfig before anything is built, so a stale sync cannot ship under the wrong name.
# Output: build/release/testflight-<build>/{bibleview,archive,export,upload}.log + summary.txt (last line result=...);
#   the archive in build/release/AndBible-<build>.xcarchive. The key is read from <volume>/AuthKey_<key-id>.p8 only.
set -uo pipefail
[ $# -ge 7 ] || { echo "usage: $0 <volume> <key-id> <issuer-id> <team-id> <build> <version> <commit> [upload|export] [reuse]" >&2; exit 2; }
for a in "$1" "$2" "$3" "$4" "$5" "$6" "$7"; do [ -n "$a" ] || { echo "empty argument (volume, key-id, issuer, team, build, version and commit are all required)" >&2; exit 2; }; done
VOL="$1" KEY_ID="$2" ISSUER="$3" TEAM="$4" BUILD="$5" VERSION="$6" COMMIT="$7" DEST="${8:-upload}" MODE="${9:-}"
KEY="$VOL/AuthKey_${KEY_ID}.p8"
BUNDLE_ID=org.andbible.ios ICLOUD=iCloud.org.andbible.ios
# xcodebuild -exportArchive must not see Homebrew tools (rsync). ANDBIBLE_TEST_EXPORT_PATH is a test-only seam.
EXPORT_PATH=/usr/bin:/bin:/usr/sbin:/sbin
if [ -n "${ANDBIBLE_TEST_EXPORT_PATH:-}" ]; then
	EXPORT_PATH="$ANDBIBLE_TEST_EXPORT_PATH"; echo "WARNING: ANDBIBLE_TEST_EXPORT_PATH is set (test seam): export PATH=$EXPORT_PATH" >&2
fi
REL=build/release
OUT="$REL/testflight-$BUILD"; mkdir -p "$OUT"
ARCH="$REL/AndBible-$BUILD.xcarchive"
STEP=start RESULT="" CHECKS="" ARCHIVE_S="" EXPORT_S="" UPLOAD_S=""

cleanup() {
	rc=$?
	[ -n "$RESULT" ] || RESULT="FAIL $STEP"
	{
		echo "version=$VERSION"; echo "build=$BUILD"; echo "commit=$COMMIT"
		echo "archive_s=$ARCHIVE_S"; echo "export_s=$EXPORT_S"; echo "upload_s=$UPLOAD_S"
		echo "checks=$CHECKS"; echo "result=$RESULT"
	} > "$OUT/summary.txt"
	hdiutil detach "$(df "$VOL" 2>/dev/null | awk 'NR==2{print $1}')" >/dev/null 2>&1 || diskutil eject "$VOL" >/dev/null 2>&1
	echo "cleanup: $(hdiutil info 2>/dev/null | grep -c asckey) attached asckey volumes; result=$RESULT"
	exit $rc
}
trap cleanup EXIT
trap 'exit 143' TERM HUP
trap 'exit 130' INT
die() { echo "$1" >&2; exit "${2:-1}"; }

case "$DEST" in upload|export) ;; *) STEP=args; die "bad destination '$DEST' (upload|export)" 2 ;; esac
case "$MODE" in ''|reuse) ;; *) STEP=args; die "bad 9th argument '$MODE' (reuse or nothing)" 2 ;; esac
STEP=key
[ -f "$KEY" ] || die "no key at $KEY" 2

# A stale or hand-edited sync must not ship under another commit's or version's name.
STEP=xcconfig
XC=iosApp/Config/Version.xcconfig
[ -f "$XC" ] || die "no $XC" 2
xc_get() { awk -F' *= *' -v k="$1" '$1 == k { print $2; exit }' "$XC"; }
XC_VERSION=$(xc_get MARKETING_VERSION); XC_COMMIT=$(xc_get AND_BIBLE_COMMIT_HASH)
[ "$XC_VERSION" = "$VERSION" ] || die "$XC MARKETING_VERSION '$XC_VERSION' != '$VERSION'" 2
[ -n "$XC_COMMIT" ] && [ "${XC_COMMIT#"$COMMIT"}" != "$XC_COMMIT" ] || die "$XC AND_BIBLE_COMMIT_HASH '$XC_COMMIT' does not start with '$COMMIT'" 2
CHECKS="xcconfig"
echo "build number $BUILD, version $VERSION, commit $COMMIT, destination $DEST${MODE:+, reusing the archive}"
APP="$ARCH/Products/Applications/AndBible.app"

if [ "$MODE" = reuse ]; then
	STEP=reuse
	[ -f "$APP/Info.plist" ] || die "no archive to reuse at $ARCH" 3
	A_BUILD=$(plutil -extract CFBundleVersion raw -o - "$APP/Info.plist" 2>/dev/null)
	A_VERSION=$(plutil -extract CFBundleShortVersionString raw -o - "$APP/Info.plist" 2>/dev/null)
	[ "$A_BUILD" = "$BUILD" ] && [ "$A_VERSION" = "$VERSION" ] \
		|| die "archive $ARCH is $A_VERSION ($A_BUILD), not $VERSION ($BUILD)" 3
	CHECKS="$CHECKS reuse"
	echo "reusing $ARCH"
else
STEP=bibleview
( cd app/bibleview-js
  stamp=node_modules/.mac-remote-lock-sha; sha=$(shasum package-lock.json | cut -d' ' -f1)
  [ "$(cat $stamp 2>/dev/null)" = "$sha" ] || { npm ci --no-audit --no-fund && echo "$sha" > $stamp; } &&
  npm run build-production ) > "$OUT/bibleview.log" 2>&1 || { tail -40 "$OUT/bibleview.log"; exit 3; }
echo "bibleview-js production build OK"
STEP=xcodegen
( cd iosApp && xcodegen generate --quiet ) || exit 3

AUTH=(-allowProvisioningUpdates -authenticationKeyPath "$KEY" -authenticationKeyID "$KEY_ID" -authenticationKeyIssuerID "$ISSUER")
STEP=archive
t0=$(date +%s)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release -sdk iphoneos \
	-destination 'generic/platform=iOS' -archivePath "$ARCH" \
	CODE_SIGNING_ALLOWED=NO DEVELOPMENT_TEAM="$TEAM" CURRENT_PROJECT_VERSION="$BUILD" archive > "$OUT/archive.log" 2>&1
rc=$?; ARCHIVE_S=$(( $(date +%s)-t0 )); echo "archive rc=$rc in ${ARCHIVE_S}s"
[ $rc = 0 ] || { grep -E "error:|OutOfMemory|FAILED" "$OUT/archive.log" | sort -u | head -20; exit 1; }

plutil -p "$APP/Info.plist" | grep -E '"CFBundle(Identifier|ShortVersionString|Version|DisplayName)"|CFBundleTypeName'
# The unsigned archive carries no entitlements: ad hoc re-sign with the Release-expanded entitlements.
STEP=resign
sed "s/\\\$(ICLOUD_CONTAINER)/$ICLOUD/g" iosApp/iosApp/iosApp.entitlements > "$OUT/release.entitlements"
grep -q '\$(' "$OUT/release.entitlements" && die "unexpanded variable in entitlements"
codesign --force --sign - --entitlements "$OUT/release.entitlements" --timestamp=none "$APP" || exit 1
fi

cat > "$OUT/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>method</key><string>app-store-connect</string>
<key>destination</key><string>export</string>
<key>teamID</key><string>$TEAM</string>
<key>signingStyle</key><string>automatic</string>
<key>uploadSymbols</key><true/>
<key>manageAppVersionAndBuildNumber</key><false/>
</dict></plist>
PLIST
# Export first (to inspect the signed IPA), then upload the same archive.
STEP=export
rm -rf "$OUT/export"
t0=$(date +%s)
PATH="$EXPORT_PATH" xcodebuild -exportArchive -archivePath "$ARCH" -exportOptionsPlist "$OUT/ExportOptions.plist" \
	-exportPath "$OUT/export" "${AUTH[@]}" > "$OUT/export.log" 2>&1
rc=$?; EXPORT_S=$(( $(date +%s)-t0 )); echo "export rc=$rc in ${EXPORT_S}s"
[ $rc = 0 ] || { grep -E "error|Signing|certificate|profile" "$OUT/export.log" | sort -u | head -15; exit 1; }

# IPA checks before upload: any miss stops here, nothing is uploaded.
STEP=ipa-checks
IPA=$(ls "$PWD/$OUT/export"/*.ipa 2>/dev/null | head -1)
[ -n "$IPA" ] || die "no IPA in $OUT/export"
rm -rf "$OUT/ipa"; mkdir -p "$OUT/ipa"; ( cd "$OUT/ipa" && unzip -q "$IPA" ) || die "cannot unzip the IPA"
SAPP=$(ls -d "$OUT/ipa"/Payload/*.app 2>/dev/null | head -1)
[ -n "$SAPP" ] || die "no app in the IPA"
SIG=$(codesign -dv --verbose=4 "$SAPP" 2>&1)
echo "$SIG" | grep -E "^(Identifier|Authority|TeamIdentifier)"
codesign -d --entitlements :- "$SAPP" 2>/dev/null > "$OUT/ipa-entitlements.txt"
plutil -p - < "$OUT/ipa-entitlements.txt" 2>/dev/null
check() { # check <label> <ok-command...>
	local label="$1"; shift
	if "$@"; then CHECKS="$CHECKS $label=ok"; else CHECKS="$CHECKS $label=FAIL"; die "IPA check failed: $label"; fi
}
sig_has() { echo "$SIG" | grep -qxF "$1"; }
sig_starts() { echo "$SIG" | grep -qF "$1" && echo "$SIG" | grep "^Authority=" | grep -q "^$1"; }
ents_has() { grep -q "$1" "$OUT/ipa-entitlements.txt"; }
plist_is() { [ "$(plutil -extract "$1" raw -o - "$SAPP/Info.plist" 2>/dev/null)" = "$2" ]; }
# get-task-allow: false or absent passes (absent = not debuggable); only true fails.
gta_not_true() { [ "$(plutil -extract get-task-allow raw -o - "$OUT/ipa-entitlements.txt" 2>/dev/null)" != true ]; }
check identifier sig_has "Identifier=$BUNDLE_ID"
check team sig_has "TeamIdentifier=$TEAM"
check authority sig_starts "Authority=Apple Distribution:"
check icloud ents_has "$ICLOUD"
check get-task-allow gta_not_true
check version plist_is CFBundleShortVersionString "$VERSION"
check build plist_is CFBundleVersion "$BUILD"
[ "$DEST" = upload ] || { RESULT="PASS export"; echo "PASS export (no upload)"; exit 0; }

STEP=upload
sed 's|<string>export</string>|<string>upload</string>|' "$OUT/ExportOptions.plist" > "$OUT/ExportOptions.upload.plist"
t0=$(date +%s)
PATH="$EXPORT_PATH" xcodebuild -exportArchive -archivePath "$ARCH" -exportOptionsPlist "$OUT/ExportOptions.upload.plist" \
	-exportPath "$OUT/upload" "${AUTH[@]}" > "$OUT/upload.log" 2>&1
rc=$?; UPLOAD_S=$(( $(date +%s)-t0 )); echo "upload rc=$rc in ${UPLOAD_S}s"
grep -E "Upload succeeded|EXPORT SUCCEEDED|EXPORT FAILED|error|ERROR|ITMS" "$OUT/upload.log" | sort -u | head -20
[ $rc = 0 ] || exit 1
RESULT="PASS upload"
echo "PASS upload $BUILD"
