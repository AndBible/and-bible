# Cloud Development Environment Notes

This directory contains scripts for setting up the AndBible development environment in Claude Code cloud sessions.

## Setup

Run once per session (idempotent):
```bash
bash ai-local/setup-cloud-env.sh
```

Then activate the environment:
```bash
source ai-local/activate-env.sh
```

## Known Environment Constraints

### Proxy Configuration
The cloud environment routes internet traffic through an authenticated HTTP proxy. Two constraints affect Android development:

1. **sdkmanager cannot authenticate with the proxy** — `sdkmanager` uses `java.net.HttpURLConnection` for HTTPS tunneling (CONNECT method), which does not work with the JWT-authenticated proxy. Android SDK components must be downloaded manually via `curl` (which handles the proxy correctly).

2. **`*.google.com` in Java nonProxyHosts breaks Gradle** — The environment sets `JAVA_TOOL_OPTIONS` with `*.google.com` in `nonProxyHosts`, causing Java to attempt direct connections to `dl.google.com`. Since there is no direct internet access, DNS resolution fails. `activate-env.sh` removes `*.google.com` and `*.googleapis.com` from `nonProxyHosts` to fix this.

### Robolectric Test Artifacts
Robolectric downloads `android-all-instrumented` jars at test runtime using its own Maven resolver, which also cannot authenticate with the proxy. `setup-cloud-env.sh` pre-downloads the required jar to `~/.m2/repository` so Robolectric finds it locally.

### Expected Test Results
- **Vue.js tests**: All 235+ tests pass
- **Android unit tests**: ~780 pass, ~140 fail with `NullPointerException` or `IndexOutOfBoundsException` — these are pre-existing failures caused by missing JSword Bible data files (not available in the test environment). This is expected behavior in the cloud environment.

## Files

- `setup-cloud-env.sh` — Full environment setup (run once)
- `activate-env.sh` — Quick environment activation for new shells (source it)
- `CLAUDE.md` — This file (included by the root CLAUDE.md via `@./ai-local/CLAUDE.md`)
