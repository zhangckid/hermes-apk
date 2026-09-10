# Public release checklist

## Owner decisions — blockers before publishing

- [x] Project code/documentation license selected: MIT; full `LICENSE` text included.
- [ ] Confirm ownership/redistribution rights for the supplied app icon, or replace it with an original asset.
- [ ] Audit the speech AAR's bundled native dependencies and model-weight licenses/notices. The top-level
      sherpa-onnx license alone is not a complete transitive binary license audit.
- [ ] Record the supported Hermes web backend source/revision and setup instructions.
- [ ] Create the repository, configure a private security/abuse reporting channel and maintainer ownership.
- [ ] Establish release signing and secure key backup outside the repository; do not publish the debug key.

## Repository review

- [ ] Run `python3 scripts/check_repository.py` and a dedicated secret scanner on the staged Git contents.
- [ ] Review `git diff --cached` manually after initializing the repository. Automated scans are not proof
      that no secrets, private conversations or unauthorized assets are present.
- [ ] Never stage `.local-backups/`, old APKs, Gradle caches, keystores, cookies, recordings or local settings.
- [ ] Review the 55 MB vendored speech AAR and decide whether to keep it in Git or move to a separately
      verified download flow; never substitute an unverified mirror.
- [ ] Run all tests, debug/release builds and lint from a clean checkout; verify the GitHub workflow.
- [ ] Inspect release APK contents/dependencies to confirm SSH and bundled terminal assets are absent.
- [ ] Perform the device checklist and explicitly record any untested platforms/backend versions.

## Suggested initial Git review

After the owner decisions above are resolved, initialize a local repository and inspect the index before
adding any remote. These commands are instructions only; they have not been executed automatically.

```sh
git init -b main
git add .gitignore .gitattributes .editorconfig .github README.md CHANGELOG.md CONTRIBUTING.md SECURITY.md CODE_OF_CONDUCT.md THIRD_PARTY_NOTICES.md docs scripts app/src app/libs app/build.gradle.kts app/proguard-rules.pro build.gradle.kts settings.gradle.kts gradle.properties gradle gradlew gradlew.bat
git add LICENSE
git diff --cached --stat
git diff --cached --check
python3 scripts/check_repository.py
```

Inspect all staged files, including artwork and the vendored AAR, and run a dedicated secret scanner.
Only then make the initial commit and configure your chosen GitHub remote. Do not force-add ignored backups
or signing material if an ignore rule surprises you.

## Publish

- [ ] Fill release notes with actual results and known limitations, mark the changelog release date,
      and tag the exact reviewed commit.
- [ ] Publish signed release artifacts and checksums through GitHub Releases, not the source tree.
- [ ] Enable appropriate branch protection, dependency alerts and secret scanning where available.

The current workspace has not been initialized or pushed as a GitHub repository. No remote publication
is performed by these documents or by the build workflow.

References: [GitHub community profiles](https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/about-community-profiles-for-public-repositories),
[private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/working-with-repository-security-advisories/configuring-private-vulnerability-reporting-for-a-repository).
