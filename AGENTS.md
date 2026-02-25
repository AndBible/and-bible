# AndBible: Local Contribution Workflow

Use this file as the default workflow for all coding tasks in this repository.

## 1) Pick base branch by change type

- New feature: base `develop`
- Bug fix or small improvement: base `current-stable`

## 2) Start from clean upstream base

```bash
git fetch upstream
git switch <base-branch>
git pull --ff-only upstream <base-branch>
```

## 3) Create a focused branch

Branch format (from `CONTRIBUTING.md`):

`<type>/#<issue-number>_<short_name>`

Types: `feature`, `bugfix`, `improve`, `docs`, `refactor`

Example:

```bash
git switch -c 'improve/#123_one_view_chooser_layout'
```

If there is no issue number, use `#0` as placeholder.

## 4) Keep PR scope small

- One thing per PR
- Do not mix unrelated refactors/tooling changes
- UI changes should include screenshots when possible

## 5) Validate before commit

Minimum local check for Android changes:

```bash
./gradlew :app:assembleStandardGithubDebug -x jsBuild --no-daemon
```

## 6) Commit style

- Atomic commit(s)
- Imperative, descriptive subject
- Explain why in body when useful
- If tied to an issue, use `Fixes #NNN (...)`

Example:

```bash
git commit -m "Improve one-view passage chooser usability"
```

## 7) Push and open PR from fork

```bash
git push -u origin '<branch-name>'
gh pr create --base <base-branch> --head <fork-owner>:<branch-name>
```

PR description should include:

- What changed
- Benefits
- Possible side effects
- Screenshots (if UI change)

## 8) After review

- Address review comments in same branch
- Push updates to same PR
- Keep changes focused; split unrelated work to separate PRs
