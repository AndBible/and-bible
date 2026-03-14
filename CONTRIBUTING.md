# How to contribute code

## Building And Bible

### Prerequisites

- **Java 17** (OpenJDK 17 or higher)
- **Node.js 20.x** — download from [https://nodejs.org/](https://nodejs.org/)
- **Android Studio** — download from [https://developer.android.com/studio](https://developer.android.com/studio)
- **Android SDK** (API level 23 or higher, installed via Android Studio)

### Setup

1. Open a terminal and clone the repository:
```
git clone https://github.com/AndBible/and-bible.git
```

2. In Android Studio, select **File > Open** and open the `and-bible/and-bible` folder (the one with the green compass icon).

3. Build and run the app. Node.js dependencies and the Vue.js frontend are built automatically as part of the Gradle build.

### Run a separate debug app on your device

It is possible to install And Bible as 2 different IDs on your device — effectively 2 installs on one device. To build the debug version as a separate app, open `local.properties` in the project root and add:

```
APP_SUFFIX=.debug
```

Now when you build, a separate And Bible application will be created on your device.

## Development tools

Tuomas uses (and recommends) **Android Studio** for native Android/Kotlin code and **WebStorm** for Vue.js/TypeScript development. A free alternative for Vue.js development is **Visual Studio Code** with the Volar extension for Vue.js support.

## Running tests

### CI runs tests automatically

GitHub Actions runs all tests (Android unit tests, Vue.js tests) automatically on every pull request. **You do not need test modules for normal contributions** — CI has them.

### Running tests locally

If you want to run tests locally yourself:

- **Vue.js tests** (fast, ~5-6 seconds):
  ```
  cd app/bibleview-js && npm run test:ci
  ```

- **Android unit tests**:
  ```
  ./gradlew testStandardGoogleplayDebugUnitTest
  ```

For running tests locally, you need to install test modules. One module in the test package cannot be distributed publicly — contact us at **help.andbible@gmail.com** if you need it.

## Contributing code

- Fork the repo
- Consider writing unit tests (how important this is depends on the type of change)
- Create a pull request from your fork's feature branch (see [naming convention](#branch-naming)) towards one of these branches:
  - `develop` — for new features
  - `current-stable` — for bugfixes or minor improvements

### Commit messages

Commits should be **atomic and descriptive**. A good commit message explains *why* the change was made, not just what changed.

- Use a short imperative subject line (e.g. `Fix crash when bookmarks list is empty`)
- Optionally add a blank line followed by a more detailed explanation of the motivation
- If the commit fixes a GitHub issue, start the message with `Fixes #NNN (short description)` — this automatically closes the issue when merged

AI tools (Copilot, Claude, etc.) are welcome for drafting commit messages.

Example:
```
Fixes #1234 (crash when bookmarks list is empty)

BookmarkControl now checks for an empty list before accessing
the first element, preventing a NullPointerException on startup.
```

### Pull request guidelines

- **One thing per PR**: each PR should address a single concern — a bug fix, a feature, or a refactor. Mixing unrelated changes makes review harder and slower.

- **PR description**: write a clear description of what was changed and why. AI tools are welcome for drafting the description.

- **Screenshot**: if the change affects the UI, include a before/after screenshot or GIF. This greatly speeds up review.

- **Size**: keep PRs small and focused. Large, sprawling PRs are hard to review. All PRs must be reviewed by Tuomas before merging — if a PR is very large or complex, there is no guarantee it will get reviewed promptly (or at all), regardless of contribution value.

- **Code documentation**: write readable, self-explanatory code. Add comments where the logic is non-obvious, but avoid over-documenting obvious code.

### Automated review process

When you open a pull request, **GitHub Copilot will be automatically requested as a reviewer**. This is the first step of the review process:

1. Copilot reviews your code and may leave comments or suggestions.
2. Address Copilot's feedback by either fixing the issues (push new commits) or replying to the comment thread with your reasoning if you disagree.
3. Once Copilot's feedback is addressed, a human maintainer will review your PR.

Human review will not begin until Copilot's feedback is addressed. Responding promptly to Copilot comments speeds up the overall process.

### AI tools and code responsibility

AndBible welcomes contributions that use AI coding assistants (Copilot, Claude, ChatGPT, etc.), but **every line of code you submit must be personally reviewed and understood by you**. AndBible maintains 100% human-approved code — this is not a vibe-coding project.

Before submitting a pull request:

1. **Review your own code.** Read through every change as if you were the reviewer. Ask yourself: does this fit the existing codebase? Is the approach sound? Are there edge cases?
2. **Run an AI self-review.** If you used an AI tool to generate code, ask it to review the diff for bugs, style issues, and potential problems. Fix anything significant it finds before submitting. You may reasonably disagree with AI review suggestions, but ignoring the review entirely is not acceptable.
3. **Take personal responsibility.** You are the author. If something breaks, "the AI wrote it" is not an acceptable answer. Only submit code you can explain and defend.

Contributors are expected to have sufficient software development experience and understanding to evaluate whether their solution is correct and appropriate for the AndBible codebase. If you are not yet confident in your ability to review your own code, consider contributing in other ways (testing, documentation, translations) while building your skills.

### Code style guide

- Do not write too long lines (120 characters should be your guide)
- Preferably write new code in Kotlin (if you need to create a new file, create a Kotlin file)
- If you are planning to touch a lot of Java files, please discuss with Tuomas first — we are gradually migrating Java files to Kotlin and that should probably be done first

### Branch naming

Please name your branches using the following syntax: `<type>/#<issue-number>_<name>`, where

- `<type>` can be one of:
  - `feature`
  - `bugfix`
  - `improve`
  - `docs`
  - `refactor`
- `<issue-number>` is the GitHub issue number (use it where applicable; docs or refactor branches may not have an issue)
- `<name>` is a short human-readable name, spaces replaced with underscores

Example: `feature/#100_improve_layout`
