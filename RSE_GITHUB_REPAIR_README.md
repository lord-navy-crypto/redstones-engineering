# RSE Alpha 1.0.3 — GitHub Repair Overlay

This overlay intentionally does **not** replace `src/`.

## Why

Archive comparison shows that the uploaded Alpha 1.0.3 merged source tree already contains the pneumatic compile hotfix, and GitHub `main` matches the key hotfix Java blobs. Replacing the whole repository with that partial reconstructed tree would be unsafe because the GitHub repository also contains the complete NeoForge/Gradle project root and additional Alpha 1.0.3 closed-loop/diagnostics work.

## What this overlay fixes

- Stops ignoring `.github/workflows/`.
- Adds a GitHub Actions build-verification workflow using Java 21.
- Ignores accidental `javac.*.args` and JVM crash scratch files.
- Runs available RSE static verifiers before `compileJava` and `clean build`.

## One-time cleanup after applying

From the repository root:

```zsh
rm -f javac.*.args
sed -i '' '/^javac\..*\.args$/d' ALPHA1_0_3_CHANGED_FILES.txt 2>/dev/null || true
git add .gitignore .github/workflows/build.yml ALPHA1_0_3_CHANGED_FILES.txt
git rm --cached 'javac.20260903_025519.args' 2>/dev/null || true
git status --short
```

Then verify locally:

```zsh
chmod +x gradlew
./gradlew compileJava --no-daemon --stacktrace
./gradlew clean build --no-daemon --stacktrace
```

Only after those succeed should the build be called Gradle/NeoForge build-confirmed. `runClient` should be tested locally on the Mac, not in headless GitHub Actions.
