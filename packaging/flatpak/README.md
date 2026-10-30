# Flatpak packaging

The manifest targets x86-64 and uses the freedesktop 25.08 SDK, OpenJDK 17 and Node 24. The JCEF archive and Android SDK components are pinned build inputs; the application runs with its bundled Java runtime. Network and Secret Service are available at runtime, but host files are accessed through portals rather than a home-directory grant.

## Regenerating sources

Install `flatpak-node-generator` from flatpak-builder-tools commit `9d86d336a39b9af38b86f114b6ce5820fe260f91`, and provide JDK 17, the Android SDK, Node 24 and network access. Run `bash packaging/flatpak/update-sources.sh` from the repository root. The script generates npm cache sources without Playwright browsers, generates Gradle sources per project with flatpak-gradle-generator 1.8.0, merges them, downloads each remote input to verify the existing digest and add SHA-256, and replaces the source lists only on success. Review the diff and validate a clean offline build after changes to dependencies. Identical Maven mirror content is coalesced by digest; differing bytes at the same Maven path are retained in alternate repository roots, which the offline init script also registers. Only the reviewed, dependency-equivalent Kotlin marker POM digest pair may differ; any other conflicting Maven content fails generation and requires inspection. `python3 -I packaging/flatpak/test-source-merge.py` exercises this behavior. The classpath selection intentionally includes JVM development/compiler tooling and plugin buildscript dependencies as well as release runtime dependencies; a slightly larger pinned closure is preferable to missing lazily created packaging inputs.

Install the pinned node generator in an isolated tool environment:

```bash
pipx install 'git+https://github.com/flatpak/flatpak-builder-tools.git@9d86d336a39b9af38b86f114b6ce5820fe260f91#subdirectory=node'
```

Gradle downloads the pinned generator through the generation-only init script. The regeneration script discards only its enumerated generator outputs before running, then requires every output, so unrelated stale project files cannot enter the merged list.

## Local build

Install flatpak-builder and the 25.08 freedesktop runtime, SDK and OpenJDK 17 and Node 24 SDK extensions. Populate flatpak-builder's source cache first with `flatpak-builder --download-only build-dir packaging/flatpak/org.andbible.AndBible.yml`; then use `flatpak-builder --force-clean --disable-download --repo=repo build-dir packaging/flatpak/org.andbible.AndBible.yml`. The second command must succeed without downloads; an existing Gradle cache is not a substitute for a complete source list. Install the resulting repository with `flatpak build-update-repo repo` and `flatpak install --user repo org.andbible.AndBible`, then run `flatpak run org.andbible.AndBible`.

## Flathub submission

Fork `flathub/flathub`, create its `new-pr` branch and add the manifest, source lists and packaging files. Submit the branch as a pull request and follow Flathub review. Domain verification is a separate maintainer action: publish the Flathub verification token at `https://andbible.org/.well-known/org.flathub.VerifiedApps.txt` as directed by Flathub. Neither submission nor domain verification is performed by the build.
