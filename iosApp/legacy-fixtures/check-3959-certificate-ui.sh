#!/bin/bash
# Run through mac-remote.sh run, under its simulator execution lock.
set -euo pipefail
scratch=$(mktemp -d /tmp/andbible-3959-certificate.XXXXXX)
server_pid=""
swap_pid=""
cleanup() {
  test -z "$swap_pid" || kill "$swap_pid" 2>/dev/null || true
  if test -s "$scratch/changed-pid"; then kill "$(<"$scratch/changed-pid")" 2>/dev/null || true; fi
  test -z "$server_pid" || kill "$server_pid" 2>/dev/null || true
  if test -n "${id:-}"; then
    xcrun simctl shutdown "$id" 2>/dev/null || true
    xcrun simctl delete "$id"
  fi
  rm -rf "$scratch"
}
trap cleanup EXIT
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -subj '/CN=localhost/O=AndBible disposable test' \
  -keyout "$scratch/key.pem" -out "$scratch/cert.pem" > "$scratch/openssl.log" 2>&1
python3 -I iosApp/legacy-fixtures/checklist-tls-server.py \
  "$scratch/cert.pem" "$scratch/key.pem" "$scratch/port" > "$scratch/server.log" 2>&1 &
server_pid=$!
for _ in {1..50}; do test ! -s "$scratch/port" || break; sleep 0.1; done
test -s "$scratch/port"
port=$(<"$scratch/port")
fingerprint=$(openssl x509 -in "$scratch/cert.pem" -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':')
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -subj '/CN=localhost/O=AndBible disposable test' \
  -keyout "$scratch/changed-key.pem" -out "$scratch/changed-cert.pem" > "$scratch/changed-openssl.log" 2>&1
changed=$(openssl x509 -in "$scratch/changed-cert.pem" -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':')
(
  for _ in {1..900}; do test ! -e "$scratch/request" || break; sleep 1; done
  test -e "$scratch/request" || exit 1
  kill "$server_pid"
  # Wait for the original listener to release only its own loopback socket.
  sleep 1
  python3 -I iosApp/legacy-fixtures/checklist-tls-server.py \
    "$scratch/changed-cert.pem" "$scratch/changed-key.pem" "$scratch/changed-port" "$port" >> "$scratch/server.log" 2>&1 &
  printf '%s' "$!" > "$scratch/changed-pid"
  for _ in {1..50}; do test ! -s "$scratch/changed-port" || break; sleep 0.1; done
  test -s "$scratch/changed-port" && touch "$scratch/ready"
  wait
) &
swap_pid=$!
export TEST_RUNNER_TASK7_CERTIFICATE_SWAP_DIRECTORY="$scratch"
export TEST_RUNNER_TASK7_CERTIFICATE_ACCEPTANCE=1 TEST_RUNNER_TASK7_SYNC_ACCEPTANCE=1
export TEST_RUNNER_TASK7_SYNC_FIXTURE_JSON
TEST_RUNNER_TASK7_SYNC_FIXTURE_JSON=$(python3 -I -c '
import json,sys
print(json.dumps(dict(serverURL="https://localhost:"+sys.argv[1], username="fixture", password="disposable", trustedCertificateSha256=sys.argv[2], changedCertificateSha256=sys.argv[3], folderPath="", marker="3959", reference="John.3.16", versePhrase="For God", noteAccessibilityLabel="3959")))
' "$port" "$fingerprint" "$changed")
id=$(xcrun simctl create "3959 Certificate disposable" com.apple.CoreSimulator.SimDeviceType.iPhone-16 com.apple.CoreSimulator.SimRuntime.iOS-18-6)
# The device was created by this invocation; deletion can never target another container's profile.
trap cleanup EXIT
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
