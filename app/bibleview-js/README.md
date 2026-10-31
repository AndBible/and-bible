# BibleView-JS

Vue 3 and TypeScript Bible rendering, bundled with Vite for the native WebView.
Use Node.js 24 (at least 24.15.0 for the current development dependencies).

## Development

```sh
npm ci
npm run dev
```

## Validation

```sh
npm run test:ci
npm run lint
npm run type-check
npm run build-debug
npm run build-production
```

`npm run build` checks types and builds in parallel. `npm run lint-fix` applies
ESLint fixes. The flat configuration in `eslint.config.mjs` preserves the Vue
essential and TypeScript base rules; it does not opt into the opinionated
TypeScript recommended preset.

## Dependency maintenance

Keep the existing `~` ranges and the exact Playwright pin when updating dependencies;
commit `package-lock.json`. Update Vue's runtime, compiler and reactivity packages
together. `vuedraggable` must stay on its Vue 3-compatible 4.x line: the registry's
`latest` tag still points to the Vue 2 line.

TypeScript 6 is intentional. Although `vue-tsc` declares a broad TypeScript peer
range, `@typescript-eslint/parser` 8.72.1 supports TypeScript below 6.1, not 7.
Node types stay on the supported Node 24 runtime line.

The development-only `glob` and `minimatch` overrides select patched versions while
upstream lint tooling still requests older majors. Revisit them when upstream
requirements catch up. Verify the complete validation commands after changing them.

### Security audit snapshot (2026-11-01)

`npm audit --json` reports vulnerable package nodes (including dependent packages),
not the number of unique advisories. For this update:

| Stage | Vulnerable nodes | Unique advisories | Packages with direct advisory records |
| --- | ---: | ---: | ---: |
| Original lockfile | 58 (32 moderate, 22 high, 4 critical) | 124 | 29 |
| Compatible security updates | 56 (52 moderate, 2 high, 2 critical) | 13 | 8 |
| Final lockfile | 34 (33 moderate, 1 high, 0 critical) | 8 | 4 |

The remaining findings are **only inside npm 12.2.1's bundled dependencies**:
`glob`, `minimatch`, `ip-address` and `undici`. The latest npm release still bundles
these affected versions; npm overrides do not replace bundled dependencies.
Keep this limitation visible rather than removing the build's local npm dependency
merely to hide it from the lockfile scanner. Recheck when npm publishes a fix.

`npm audit --omit=dev` reports **zero vulnerabilities**. Vitest 5 removes the old
vulnerable worker/mocker dependency chain; the sanitizer, lodash and Playwright
advisories are also addressed. OSV Scanner was not installed, so the counts above
come from npm audit and should not be compared directly with another scanner's counts.

The original suite has 699 passing tests and 3 skips across 46 files. The updated
suite has 702 passing tests and the same 3 skips across 47 files, including new
checks of actual FontAwesome SVG rendering, legacy aliases and custom icons.
FontAwesome 7, Sass, Vue, Vite, Color and Marked can affect rendered output:
the Android Roborazzi gate belongs to the integrating change, in addition to
building the APK with its embedded bundle.
