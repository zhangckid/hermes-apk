# 1.0.0 verification — 2026-09-10

First public version: `versionName=1.0.0`, `versionCode=19`. The increasing code preserves upgrades from private builds. Debug signing certificate matches the previous build.

## Verified locally

- Unit/UI regression suite: 103 cases, 102 passed, 1 optional live-service test skipped, no failures/errors.
- English default language policy tested on API 28 and 35 with Chinese system qualifiers; explicit Chinese/English choices preserved.
- Native Compose UI tests verify the server field is empty, its example is visible without focus, and saved addresses are not overwritten.
- README images are actual Compose view renders using local demo messages on Robolectric API 35, not physical-device or live-backend screenshots.
- Debug APK assembled and signature verified; Android lint: 0 errors, 30 warnings, 1 hint.
- Repository hygiene, documentation links, XML and vendored binary checksums passed.

Debug APK SHA-256:

```text
4e1d03a482e378cd78538ed357a9277e0fe0055feb3947bdd1633e8b64b9b54f
```

No physical phone was connected and no real backend or account was used. First-launch locale restoration, input methods, speech and reconnect behavior still require device acceptance. Public redistribution also requires the remaining checks in [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).
