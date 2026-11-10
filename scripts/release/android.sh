#!/usr/bin/env bash
# Android branch of the internal release: build the signed standardGoogleplayRelease AAB from the opened secrets bundle,
# check it, and upload it to the Google Play internal track. Spec
# docs/superpowers/specs/2026-11-10-release-internal-one-touch-design.md §5.4/§5.5/§9.1.
#
# Usage: scripts/release/android.sh build|upload|verify|reusable [--dry-run]
#   build     gradle-release.sh bundleStandardGoogleplayRelease (signing properties from the bundle session), copy the AAB
#             to .local/release/<versionCode>/android/andbible-<versionName>.aab, verify, write artifact.env
#   verify    the AAB is signed by the bundle keystore's certificate (alias andbible), its versionCode/versionName equal
#             version.properties, and its SHA-256 equals artifact.env (when present)
#   upload    verify, then fastlane supply to track internal (release status completed, every --skip_upload_* true)
#   reusable  exit 0 iff artifact.env matches HEAD, version.properties and the AAB's SHA-256 (no secrets needed)
#   --dry-run print the build/upload commands (secrets as <session>/...) and run nothing
# Needs RELEASE_SECRETS_DIR (lib-secrets.sh secrets_open_bundle) for build, verify and upload.
# Env: REPO_ROOT, GRADLE_RELEASE (scripts/release/gradle-release.sh), FASTLANE (fastlane), BUNDLETOOL_DUMP (a command
#      called as `<cmd> <aab> <xpath>`; default aab-manifest.py, which reads the proto manifest), KEYTOOL, JARSIGNER.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${REPO_ROOT:-$(cd "$HERE/../.." && pwd)}"
GRADLE_RELEASE="${GRADLE_RELEASE:-$HERE/gradle-release.sh}"
FASTLANE="${FASTLANE:-fastlane}"
KEYTOOL="${KEYTOOL:-keytool}"
JARSIGNER="${JARSIGNER:-jarsigner}"
PACKAGE=net.bible.android.activity
CMD="${1:-}"
DRY=0
[ "${2:-}" = --dry-run ] && DRY=1
case "$CMD" in build|upload|verify|reusable) ;; *) echo "usage: android.sh build|upload|verify|reusable [--dry-run]" >&2; exit 2 ;; esac
die() { echo "android: $*" >&2; exit 1; }

# shellcheck source=lib-secrets.sh
. "$HERE/lib-secrets.sh"
VERSION_FILE="$REPO/version.properties"
# shellcheck source=../lib-version.sh
. "$REPO/scripts/lib-version.sh"

VC=$(version_code) || die "no versionCode"
VN=$(version_name) || die "no versionName"
OUT="$REPO/.local/release/$VC/android"
AAB="$OUT/andbible-$VN.aab"
ENVF="$OUT/artifact.env"
GRADLE_OUT="$REPO/app/build/outputs/bundle/standardGoogleplayRelease"
head10() { git -C "$REPO" rev-parse --short=10 HEAD; }
sha() { sha256sum "$1" | cut -d' ' -f1; }
envval() { sed -n "s/^$1=//p" "$ENVF" 2>/dev/null | head -n 1; }

check_clean() {
    local dirty
    dirty=$(git -C "$REPO" status --porcelain --ignore-submodules=none -- . ':(exclude)docs/superpowers' ':(exclude)app/src/test/roborazzi') \
        || die "git status failed"
    [ -z "$dirty" ] || { echo "$dirty" >&2; die "the tree is not clean (commit or stash; docs/superpowers is ignored)"; }
}

bundletool_dump() {   # bundletool_dump <aab> <xpath>: the attribute after the last '@android:'
    if [ -n "${BUNDLETOOL_DUMP:-}" ]; then "$BUNDLETOOL_DUMP" "$1" "$2"; return; fi
    python3 -I "$HERE/aab-manifest.py" "$1" "${2##*@android:}"
}

# The SHA-256 certificate fingerprint of alias andbible in the bundle keystore; the store password goes on stdin.
keystore_fp() {
    local props p12 out
    props=$(secrets_member keystore.properties) && p12=$(secrets_member andbible.p12) || return 1
    out=$(sed -n 's/^storePassword=//p' "$props" | tr -d '\r\n' \
        | "$KEYTOOL" -list -v -keystore "$p12" -storetype PKCS12 -storepass:file /dev/stdin -alias andbible 2>/dev/null)
    printf '%s\n' "$out" | awk '/SHA256:/{print $2; exit}'
}

verify() {
    local v fp_aab fp_key code name
    [ -f "$AAB" ] || die "no AAB at $AAB (run android.sh build)"
    if [ -f "$ENVF" ]; then
        [ "$(envval SHA256)" = "$(sha "$AAB")" ] || die "the AAB's SHA-256 differs from artifact.env (rebuild)"
    fi
    v=$("$JARSIGNER" -verify "$AAB" 2>&1)
    case "$v" in *"jar verified."*) ;; *) die "the AAB is not signed or its signature does not verify" ;; esac
    fp_aab=$("$KEYTOOL" -printcert -jarfile "$AAB" 2>/dev/null | awk '/SHA256:/{print $2; exit}')
    fp_key=$(keystore_fp)
    [ -n "$fp_key" ] || die "cannot read the certificate of alias andbible from the bundle keystore"
    [ -n "$fp_aab" ] && [ "$fp_aab" = "$fp_key" ] \
        || die "the AAB certificate (${fp_aab:0:11}...) is not the release keystore's (${fp_key:0:11}...)"
    code=$(bundletool_dump "$AAB" /manifest/@android:versionCode) || die "bundletool dump failed"
    name=$(bundletool_dump "$AAB" /manifest/@android:versionName) || die "bundletool dump failed"
    [ "$code" = "$VC" ] || die "AAB versionCode $code != version.properties $VC"
    [ "$name" = "$VN" ] || die "AAB versionName $name != $VN"
    echo "android: verified $AAB (certificate ${fp_key:0:11}...${fp_key: -11}, versionCode $VC, versionName $VN)"
}

reusable() {
    [ -f "$ENVF" ] && [ -f "$AAB" ] || return 1
    [ "$(envval COMMIT)" = "$(head10)" ] && [ "$(envval VERSION_CODE)" = "$VC" ] && [ "$(envval VERSION_NAME)" = "$VN" ] \
        && [ "$(envval SHA256)" = "$(sha "$AAB")" ]
}

build() {
    local head0 n props
    if [ "$DRY" = 1 ]; then
        echo "[dry-run] ANDBIBLE_SIGNING_PROPERTIES=<session>/keystore.properties $GRADLE_RELEASE bundleStandardGoogleplayRelease > $OUT/gradle.log"
        echo "[dry-run] cp $GRADLE_OUT/*.aab $AAB; verify; write $ENVF"
        return 0
    fi
    props=$(secrets_member keystore.properties) || die "no open secrets bundle"
    check_clean
    head0=$(git -C "$REPO" rev-parse HEAD)
    mkdir -p "$OUT"
    rm -f "$ENVF" "$AAB" "$GRADLE_OUT"/*.aab
    echo "android: building bundleStandardGoogleplayRelease $VN ($VC), log $OUT/gradle.log"
    ANDBIBLE_SIGNING_PROPERTIES="$props" "$GRADLE_RELEASE" bundleStandardGoogleplayRelease > "$OUT/gradle.log" 2>&1 \
        || die "Gradle failed; see $OUT/gradle.log"
    [ "$(git -C "$REPO" rev-parse HEAD)" = "$head0" ] || die "HEAD moved during the build; nothing kept"
    check_clean
    shopt -s nullglob; local aabs=("$GRADLE_OUT"/*.aab); shopt -u nullglob
    n=${#aabs[@]}
    [ "$n" = 1 ] || die "expected one AAB in $GRADLE_OUT, found $n"
    cp "${aabs[0]}" "$AAB"
    verify
    { echo "COMMIT=$(head10)"; echo "VERSION_CODE=$VC"; echo "VERSION_NAME=$VN"; echo "SHA256=$(sha "$AAB")"; } > "$ENVF"
    echo "android: built $AAB"
}

upload() {
    local json rc
    if [ "$DRY" = 1 ]; then
        echo "[dry-run] verify $AAB; $FASTLANE supply --package_name $PACKAGE --track internal --release_status completed --aab $AAB --json_key <session>/play-service-account.json --skip_upload_metadata true --skip_upload_changelogs true --skip_upload_images true --skip_upload_screenshots true --skip_upload_apk true > $OUT/upload.log"
        return 0
    fi
    json=$(secrets_member play-service-account.json) || die "no open secrets bundle"
    verify
    echo "android: uploading $VN ($VC) to Play internal, log $OUT/upload.log"
    "$FASTLANE" supply --package_name "$PACKAGE" --track internal --release_status completed --aab "$AAB" --json_key "$json" \
        --skip_upload_metadata true --skip_upload_changelogs true --skip_upload_images true \
        --skip_upload_screenshots true --skip_upload_apk true > "$OUT/upload.log" 2>&1
    rc=$?
    if [ "$rc" != 0 ]; then
        if grep -qiE 'version code .*already (been )?used|already been used|APK specifies a version code that has already been used' "$OUT/upload.log"; then
            die "Play already has versionCode $VC (bump the version); see $OUT/upload.log"
        fi
        die "fastlane supply failed (rc=$rc); see $OUT/upload.log"
    fi
    echo "android: uploaded $VN ($VC) to Play internal"
}

case "$CMD" in
build) build ;;
upload) upload ;;
verify) [ "$DRY" = 1 ] && { echo "[dry-run] verify $AAB"; exit 0; }; verify ;;
reusable) reusable ;;
esac
