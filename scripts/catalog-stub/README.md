# Catalog stub

HTTPS static file server for emulator tests of repository downloads (SWORD repositories, MyBible
registry). Serves fixtures from a local directory; no upstream requests.

```bash
python3 -I scripts/catalog-stub/catalog_stub.py --root <dir> --port 8443 [--cert C --key K] [--stall /packages/X.zip]
```

- Prints `READY <port>`, then one line per request: `<METHOD> <path> <status> <bytes>` (count the `GET`
  lines to check how often the app fetched a file). A stalled request is logged once, when its first
  chunk has been sent.
- GET and HEAD under `--root` only. No directory listing; a missing file, a directory or a `..` segment
  (also percent-encoded) is a 404.
- `--stall <url-path>` (repeatable): send the headers with the full `Content-Length` and the first 65536
  bytes, then hold the connection open until the client disconnects. Other requests stay served
  (the server is threaded).
- Certificates default to `.local/llm-stub/certs/server.{crt,key}` (SAN `IP:10.0.2.2`). Create them with
  `scripts/llm-stub/make-ca.sh`, and make the AVD trust the CA with
  `scripts/llm-stub/install-ca-on-avd.sh <serial> <avd>`. The AVD reaches the host as `https://10.0.2.2:<port>/`.

## Fixture layout

SWORD repository (the app reads `mods.d.tar.gz` for the catalog, then downloads packages):

```
<root>/mods.d.tar.gz        tar.gz of the *.conf files (mods.d/<mod>.conf)
<root>/packages/<MOD>.zip   module zip (mods.d/<mod>.conf + modules/...)
```

MyBible registry feed: a JSON file served at any path (for example `<root>/registry.json`) in the shape
of the MyBible registry, with each entry's download URL pointing at `https://10.0.2.2:<port>/<file>` and
the module files (`.SQLite3` / `.zip`) next to it under `<root>`.

## Tests

```bash
python3 -I -m unittest discover -s scripts/catalog-stub -p 'test_*.py'
```
