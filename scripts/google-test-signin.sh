#!/usr/bin/env bash
# Sign a test app in to Google Drive with the shared test account, without a human at a browser.
#
#   scripts/google-test-signin.sh login
#       Sign the persistent Chrome profile in to the test account (once; later runs reuse it).
#       Google refuses a sign-in typed into a CDP-controlled browser, so this types e-mail and
#       password with xdotool into a plain Chrome without a debugging port, then checks the result
#       over CDP (`status`). Skipped when the profile is already signed in.
#   scripts/google-test-signin.sh status
#       Print SIGNED-IN <email> (exit 0) or NOT-SIGNED-IN (exit 3).
#   scripts/google-test-signin.sh authorize '<auth url>' [--replay local|windows-vm|none]
#       Open the app's OAuth URL in the signed-in profile, choose the account, accept the consent,
#       catch the loopback redirect http://127.0.0.1:<port>/?code=... and replay it where the app
#       listens: `local` (default) curls it in this container (Linux desktop app), `windows-vm`
#       runs curl.exe inside the Windows VM over SSH (scripts/windows-vm.sh ssh), `none` only prints it.
#
# Environment (defaults in brackets):
#   GOOGLE_TEST_PROFILE  Chrome profile dir, created mode 700 [.local/google-drive/chrome-profile]
#   GOOGLE_TEST_ACCOUNT  e-mail on line 1, password on line 2, mode 600 [.local/google-drive/test-account.txt]
#   GOOGLE_TEST_SHOTS    screenshots of screens the script cannot handle [.local/google-drive/signin-shots]
#   GOOGLE_TEST_TIMEOUT  seconds to wait, including on a challenge screen [1800]
#   DISPLAY              X display for Chrome; when unset a private Xvfb runs for the call
#
# The password is typed into Google's password field only; it is never printed. A Google challenge
# (2-step verification, "verify it's you", captcha) prints `WAITING <host> "<title>" <screenshot>`
# and the script keeps polling until someone resolves it or the timeout expires (exit 3).
# Stdout lines: `SIGNED-IN <email>`, `REDIRECT <url>`, `REPLAYED <http status>`, `WAITING ...`, `TIMEOUT ...`.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PROFILE=${GOOGLE_TEST_PROFILE:-$ROOT/.local/google-drive/chrome-profile}
ACCOUNT=${GOOGLE_TEST_ACCOUNT:-$ROOT/.local/google-drive/test-account.txt}
SHOTS=${GOOGLE_TEST_SHOTS:-$ROOT/.local/google-drive/signin-shots}
TIMEOUT=${GOOGLE_TEST_TIMEOUT:-1800}

usage() { sed -n '2,25p' "$0" >&2; exit 2; }
[[ $# -ge 1 ]] || usage
cmd=$1; shift
replay=local
args=()
while [[ $# -gt 0 ]]; do
  case $1 in
    --replay) replay=$2; shift 2 ;;
    *) args+=("$1"); shift ;;
  esac
done
case $cmd in login|status) ;; authorize) [[ ${#args[@]} == 1 ]] || usage ;; *) usage ;; esac
case $replay in local|windows-vm|none) ;; *) usage ;; esac

[[ -r $ACCOUNT ]] || { echo "account file $ACCOUNT missing" >&2; exit 2; }
chmod 600 "$ACCOUNT"
mkdir -p "$PROFILE" "$SHOTS"
chmod 700 "$PROFILE" "$SHOTS"

xvfb_pid=
cleanup() { [[ -n $xvfb_pid ]] && kill "$xvfb_pid" 2>/dev/null || true; }
trap cleanup EXIT
if [[ -z ${DISPLAY:-} ]]; then
  fifo=$(mktemp -u); mkfifo "$fifo"
  Xvfb -displayfd 3 -screen 0 1280x1000x24 -nolisten tcp 3>"$fifo" >/dev/null 2>&1 &
  xvfb_pid=$!
  read -r -t 20 num <"$fifo" || { echo "Xvfb did not start" >&2; exit 4; }
  rm -f "$fifo"
  export DISPLAY=:$num
fi

run_node() { node "$ROOT/scripts/google-test-signin.mjs" "$@" --profile "$PROFILE" --account "$ACCOUNT" --shots "$SHOTS"; }
shot() { local f; f="$SHOTS/$(date -u +%Y%m%dT%H%M%S)-$1.png"; ffmpeg -loglevel error -y -f x11grab -i "$DISPLAY" -frames:v 1 "$f" && echo "$f"; }

if [[ $cmd == status ]]; then run_node status --timeout "${GOOGLE_TEST_STATUS_TIMEOUT:-60}"; exit; fi
if [[ $cmd == login ]]; then
  if run_node status --timeout 30 >/dev/null 2>&1; then echo "SIGNED-IN $(head -1 "$ACCOUNT")"; exit 0; fi
  /opt/google/chrome/chrome --user-data-dir="$PROFILE" --no-first-run --no-default-browser-check \
    --password-store=basic --disable-sync --window-size=1100,900 \
    'https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmyaccount.google.com%2F' >/dev/null 2>&1 &
  chrome_pid=$!
  win=$(timeout 30 xdotool search --sync --onlyvisible --class chrome | head -1)
  xdotool windowfocus --sync "$win" 2>/dev/null || true
  # Wait for the e-mail page, type e-mail, then the password page. Both go through stdin, never argv.
  title() { xdotool getwindowname "$win" 2>/dev/null || true; }
  # wait_title REGEX SECS: wait until the window title matches REGEX; prints it. Matching an exact
  # page title (not just "some new title") matters: Chrome briefly shows the URL as the title, and
  # typing into the wrong page would put the password into the e-mail field.
  wait_title() { local t i; for ((i = 0; i < $2; i++)); do t=$(title); grep -qE "$1" <<<"$t" && { echo "$t"; return 0; }; sleep 1; done; return 1; }
  EMAIL_PAGE='^Sign in . Google Accounts'
  PASSWORD_PAGE='^(Welcome|Hi [^-]+) . Google Chrome$'
  t=$(wait_title "$EMAIL_PAGE|$PASSWORD_PAGE" 60) || { echo "TIMEOUT no sign-in page $(shot start)"; exit 3; }
  sleep 3
  if grep -qE "$EMAIL_PAGE" <<<"$t"; then
    head -1 "$ACCOUNT" | tr -d '\n' | xdotool type --delay 60 --file -; xdotool key Return
    t=$(wait_title "$PASSWORD_PAGE" 60) || { echo "TIMEOUT no password page after the e-mail $(shot email)"; exit 3; }
    sleep 3
  fi
  sed -n 2p "$ACCOUNT" | tr -d '\n' | xdotool type --delay 60 --file -; xdotool key Return
  # Leaving the password page means the password was accepted. A page that is still a sign-in page
  # (2-step verification, "verify it's you", the password again) waits for someone to resolve it in
  # this browser session; any other page (the account page, an interstitial such as "Set a home
  # address") means the sign-in went through.
  pw_title=$t; deadline=$((SECONDS + TIMEOUT)); reported=
  while (( SECONDS < deadline )); do
    sleep 5
    t=$(title)
    if [[ $t != "$pw_title" ]] && ! grep -qiE 'sign in|welcome|verif|2-step|challenge|^hi ' <<<"$t"; then break; fi
    if [[ $t != "$reported" ]] && (( SECONDS > 25 )); then reported=$t; echo "WAITING \"$t\" $(shot challenge)"; fi
  done
  # Quit Chrome through its own shortcut so the session cookies are flushed to the profile.
  sleep 5; xdotool key --window "$win" ctrl+shift+q 2>/dev/null || true
  for ((i = 0; i < 30; i++)); do kill -0 "$chrome_pid" 2>/dev/null || break; sleep 1; done
  kill "$chrome_pid" 2>/dev/null; wait "$chrome_pid" 2>/dev/null || true
  run_node status --timeout 60; exit
fi

out=$(node "$ROOT/scripts/google-test-signin.mjs" "$cmd" --profile "$PROFILE" --account "$ACCOUNT" \
        --shots "$SHOTS" --timeout "$TIMEOUT" "${args[@]}" | tee /dev/stderr) || exit $?
[[ $cmd == authorize ]] || exit 0
url=$(sed -n 's/^REDIRECT //p' <<<"$out" | tail -1)
[[ -n $url ]] || { echo "no redirect captured" >&2; exit 4; }
case $replay in
  local) echo "REPLAYED $(curl -s -o /dev/null -w '%{http_code}' -m 30 "$url")" ;;
  windows-vm) echo "REPLAYED $("$ROOT/scripts/windows-vm.sh" ssh "curl.exe -s -o NUL -w '%{http_code}' -m 30 '$url'")" ;;
  none) ;;
esac
