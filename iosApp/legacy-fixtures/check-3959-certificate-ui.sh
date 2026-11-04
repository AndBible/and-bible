#!/bin/bash
# Run through mac-remote.sh run, under its simulator execution lock.
set -euo pipefail
scratch=$(mktemp -d /tmp/andbible-3959-certificate.XXXXXX)
server_pid=""
trap 'test -z "$server_pid" || kill "$server_pid" 2>/dev/null || true; rm -rf "$scratch"' EXIT
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -subj '/O=AndBible disposable test/CN=localhost' \
  -keyout "$scratch/key.pem" -out "$scratch/cert.pem" > "$scratch/openssl.log" 2>&1
python3 -I iosApp/legacy-fixtures/checklist-tls-server.py \
  "$scratch/cert.pem" "$scratch/key.pem" "$scratch/port" > "$scratch/server.log" 2>&1 &
server_pid=$!
for _ in {1..50}; do test ! -s "$scratch/port" || break; sleep 0.1; done
test -s "$scratch/port"
port=$(<"$scratch/port")
fingerprint=$(openssl x509 -in "$scratch/cert.pem" -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':')
export TEST_RUNNER_TASK7_CERTIFICATE_ACCEPTANCE=1 TEST_RUNNER_TASK7_SYNC_ACCEPTANCE=1
export TEST_RUNNER_TASK7_SYNC_FIXTURE_JSON
TEST_RUNNER_TASK7_SYNC_FIXTURE_JSON=$(python3 -I -c '
import json,sys
print(json.dumps(dict(serverURL="https://localhost:"+sys.argv[1], username="fixture", password="disposable", trustedCertificateSha256=sys.argv[2], folderPath="", marker="3959", reference="John.3.16", versePhrase="For God", noteAccessibilityLabel="3959")))
' "$port" "$fingerprint")
id=$(xcrun simctl create "3959 Certificate disposable" com.apple.CoreSimulator.SimDeviceType.iPhone-16 com.apple.CoreSimulator.SimRuntime.iOS-18-6)
# The device was created by this invocation; deletion can never target another container's profile.
trap 'test -z "$server_pid" || kill "$server_pid" 2>/dev/null || true; xcrun simctl shutdown "$id" 2>/dev/null || true; xcrun simctl delete "$id"; rm -rf "$scratch"' EXIT
xcrun simctl boot "$id" 2>/dev/null || true
xcrun simctl bootstatus "$id" -b
# Reset only this explicitly disposable app, never the simulator or host settings.
xcrun simctl uninstall "$id" org.andbible.ios.dev 2>/dev/null || true
mkdir -p build/shots
result="$PWD/build/3959-certificate-$(date -u +%Y%m%dT%H%M%S).xcresult"
status=0
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination "platform=iOS Simulator,id=$id" -resultBundlePath "$result" \
  -only-testing:iosAppUITests/SyncAcceptanceUITests/testCertificateCancelThenTrustLocalhost \
  > build/3959-certificate.log 2>&1 || status=$?
cp build/3959-certificate.log build/shots/3959-certificate.log
cp "$scratch/server.log" build/shots/3959-certificate-server.log
xcrun simctl io "$id" screenshot build/shots/3959-certificate-final.png >/dev/null 2>&1 || true
xcrun xcresulttool get test-results tests --path "$result" --format json > build/shots/3959-certificate-tests.json || true
exit "$status"
