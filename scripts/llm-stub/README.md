# Local LLM stub for emulator checks

Disposable, stdlib-only OpenAI-compatible HTTPS fixture. No upstream calls, real
API keys, provider account, or database changes. This is test tooling, not a
production proxy. Requires Python 3, Bash and OpenSSL. Tests require no AVD.

## Quick start

From the repository root:

```bash
scripts/llm-stub/make-ca.sh
python3 -I scripts/llm-stub/llm_stub.py --port 8123 --mode text
```

Certificates default to `.local/llm-stub/certs` (gitignored). The CA and server
keys are private, generated locally and valid for 30 days. Generation refuses to
replace a nonempty directory; use a new directory and reinstall its CA to rotate.
The server certificate has `subjectAltName=IP:10.0.2.2`. Custom paths:

```bash
scripts/llm-stub/make-ca.sh /tmp/e3-certs
python3 -I scripts/llm-stub/llm_stub.py --cert /tmp/e3-certs/server.crt --key /tmp/e3-certs/server.key
```

The default listener is `0.0.0.0:8123`, reachable from the private AVD at
`10.0.2.2:8123`. Do not expose this listener on an untrusted network; it does not
check authentication. Use `--host 127.0.0.1` for host-only checks. Stop with Ctrl-C.
Startup prints `READY <actual-port> https mode=<mode>`; each completed request is
logged to stdout with method, path, status (never prompt, header, or API key).

## Private AVD trust

Use a **disposable, rootable google_apis AVD**, not a real phone or a Play Store
image. Boot with the Android emulator's `-writable-system` flag. The helper's
optional environment setting `ANDBIBLE_EMU_WRITABLE_SYSTEM=1` enables this flag;
default boot behavior is unchanged. The controller owns boot/reboot and app
installation; the CA installer never boots, reboots or selects a device itself.

```bash
ANDBIBLE_EMU_WRITABLE_SYSTEM=1 scripts/andbible-emu.sh boot YOUR_PRIVATE_AVD
scripts/llm-stub/install-ca-on-avd.sh emulator-5554 YOUR_PRIVATE_AVD
# Optional third argument: /tmp/e3-certs/ca.crt
```

The installer requires BOTH an explicit `emulator-NNNN` serial and the expected
AVD name, validates QEMU/hardware/name before root, and routes every adb command
through `scripts/with-container-adb.sh adb` with port **5038 forced**, ignoring an
inherited host socket/port. It writes `<subject_hash_old>.0` to
`/system/etc/security/cacerts`. If remount or the write probe fails, stop: reboot
the disposable AVD with writable system; handle any verity/reboot requirement
explicitly before retrying. It never disables SELinux or automatically reboots.

On API 34+ (including E3's API 36), Conscrypt reads
`/apex/com.android.conscrypt/cacerts` instead. The installer copies the existing
APEX roots plus the test CA to `/data/local/tmp/andbible-llm-cacerts-<pid>`, labels them
`system_file`, and bind-mounts the directory in the adb shell and **both running
zygote mount namespaces** using `nsenter`. Absence of a zygote or a failed mount
is an error, not success. It checks CA readability in each namespace. These
mounts are temporary and must be recreated after every AVD reboot. Existing app
processes must be **force-stopped and restarted** to inherit the updated trust
store. Actual app TLS acceptance still requires the controller's device check;
file presence alone is not an integration pass. Delete the disposable AVD after
use; never install this CA on personal devices or export its private key.

## Configure AndBible

Select an **OpenAI-compatible / custom OpenAI** provider, not Responses,
Anthropic or Gemini. Set endpoint to **`https://10.0.2.2:8123`**, model to
`stub-model`, and a dummy non-secret API key if the form requires one (e.g.
`local-stub-not-a-key`). The wizard's models connection test lists one model.
Both root paths and `/v1` paths are accepted, so an endpoint ending in `/v1`
also works. If the guest has a global proxy configured, bypass it for `10.0.2.2`
or disable it for this private fixture; do not route fixture traffic externally.

Send a prompt: the text answer is `AndBible emulator stub response.`. Capture
the answer with the usual emulator screenshot helper. **This device step is not
part of the socket-only tests and must be recorded separately.**

The app caches successful prompt + verse results. Change the verse/prompt when
repeating a check, otherwise no new request may reach the stub.

## Modes and protocol

```bash
python3 -I scripts/llm-stub/llm_stub.py --port 8123 --mode text
python3 -I scripts/llm-stub/llm_stub.py --port 8123 --mode delay --delay-seconds 25
python3 -I scripts/llm-stub/llm_stub.py --port 8123 --mode toolcall --tool createStudyPad --tool-args '{"title":"E3 test"}'
```

Restart the server to change modes. `text` emits one assistant message. `delay`
waits before sending headers/body (also before the first SSE event); default
25 seconds, finite non-negative values only. `toolcall` emits exactly one
function call with the supplied name and JSON-object argument string, preserved
verbatim. Choose a real app tool name and its required schema for an executable
tool check; unknown names are discarded by the app adapter. A follow-up whose
latest user/tool message is a tool result returns text, avoiding an infinite
agent loop; a later user turn can request another tool. State is request-local,
so independent conversations do not consume each other's tool response.

### `toolchain` and `studypad`: create-then-finish tool runs

`--mode toolchain --chain '[{"tool":NAME,"args":{...}}, ...]'` emits one function
call per request, in order, then the plain text answer. The stub is stateless: it
counts the `role:"tool"` messages after the last user message (none = step 0, N =
step N), so a new user turn restarts the chain. Streaming and non-streaming both work.

String values anywhere in `args` may contain `{key}` (for example `{labelId}`). It is
filled from the latest `role:"tool"` message of the request: (1) parse the content as
JSON (whole text, else from the first `{`, so a trailing loop-hint is tolerated) and
take the first string/number under `key` at any depth; (2) regex
`"key"\s*:\s*"([^"]+)"`; (3) regex `key[=:]\s*(\S+)`. An unresolvable placeholder is
left verbatim. The real `createStudyPad` result is
`{"status":"success","data":{"labelId":"<32 hex>","labelName":...,"itemsCreated":N,...}}`
(`ToolResult.toJson()`), so step (1) matches.

`--mode studypad` is the preset for check BM-2: `createStudyPad` (name `Stub StudyPad`,
one text item) then `finishWithStudyPad` with `{labelId}`:

```bash
python3 -I scripts/llm-stub/llm_stub.py --port 8123 --mode studypad
```

- `GET /models` and `/v1/models`: OpenAI `object:list`, one `stub-model`.
- `POST /chat/completions` and `/v1/chat/completions`: OpenAI
  `choices[0].message`, `finish_reason` (`stop` or `tool_calls`), dummy usage.
- `stream:true`: `text/event-stream`, assistant-role delta, multiple content or
  indexed `tool_calls` argument fragments, terminal finish-reason chunk, then
  `data: [DONE]`. Tool ID/type/name appear in the initial tool delta; later
  chunks carry only index and argument fragments. Consumers must accumulate
  arguments before parsing JSON. SSE chunks are flushed individually.
- Malformed JSON/non-message bodies return 400; unknown routes return 404.
- `--insecure-http` is **only for host-side tests**. The Android app disallows
  cleartext traffic; do not use it to work around TLS.

Protocol references are `app/src/main/java/net/bible/service/llm/`:
`LlmApiAdapter.kt` (`OpenAiApiAdapter` appends `/chat/completions`),
`ApiResponseModels.kt`, `ApiRequestModels.kt`, and `DynamicModelService.kt`
(appends `/models`). The sharedCore Ktor transport currently uses `bodyAsText`,
not an SSE accumulator; the stub supports standard OpenAI SSE for streaming
clients/tests, but does not claim streaming app integration.

## Tests

```bash
python3 -I -m unittest discover -s scripts/llm-stub -p 'test_*.py'
bash -n scripts/llm-stub/make-ca.sh scripts/llm-stub/install-ca-on-avd.sh scripts/andbible-emu.sh
```

Isolated Python (`-I`) removes the repository from the import path, so
`python3 -I -m unittest scripts/llm-stub/test_llm_stub.py` cannot import `scripts`
on current Python. Use discovery as shown. Tests start real subprocesses on
free ports, exercise JSON and SSE accumulation, time delays, generate disposable
certificates, and verify a real HTTPS connection with CA and IP validation.
Installer tests replace **only** the adb executable with a command recorder;
they verify private-port routing, safety rejection and APEX namespace commands,
not successful device installation. No test runs a real adb command.

## Historical recipe replaced

This makes the scratchpad HTTPS stub in
`docs/superpowers/status/compose-emulator-verification-results.md`, session 19b,
EVR lines 1246-1252, reusable. That recipe discovered cleartext was blocked,
created a CA with the `10.0.2.2` SAN, served HTTPS on 8123, mounted the APEX CA
store in shell/zygote namespaces, and used `/v1/models` + `/v1/chat/completions`.
Unlike its `mode.txt`/`delay.txt`, this fixture uses explicit CLI options. Its
old HTTP-500/error mode and second model are not implemented by this task.
