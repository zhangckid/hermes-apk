# 1.0.0 build 20 verification — 2026-09-11

Adds explicit HTTP/HTTPS selection with persisted protocol and matching WS/WSS transport.
HTTPS remains the default for new and legacy configurations. HTTP shows an unencrypted-transport
warning; redirects and automatic HTTPS downgrade remain disabled. The authenticated client checks
the exact configured scheme, host and port. App-wide Android cleartext permission is enabled with
the project owner's explicit approval; this permission is broader than the client-level origin check.

## Local verification

- 112 unit/UI cases: 111 passed, one opt-in live-service test skipped, no failures or errors.
- Covers HTTP and IPv6 URL construction, protocol mismatch rejection, legacy settings migration,
  persisted HTTP configuration, standard/custom port switching and the visible HTTP warning.
- Local MockWebServer tests verify HTTP requests, origin restrictions, no redirect forwarding,
  and an actual WS upgrade with a text round trip.
- Debug and unsigned release builds succeeded; lint: 0 errors, 30 warnings, 1 hint.
- Packaged manifest verified to permit cleartext; versionName 1.0.0, versionCode 20.
- Debug signing certificate matches previous builds. Existing APKs were retained.
- Login screenshot regenerated from the real Compose UI in the local test environment.
- Repository hygiene and documentation link checks passed.

Debug APK SHA-256:

```text
5877612a6c750b746c48be735a570ab3ad3b27dd412bdb10bd092eecf251e7df
```

No physical device or live Hermes deployment was used. Local transport tests do not prove an arbitrary
server supports HTTP login: servers requiring Secure cookies should continue to use HTTPS. HTTP exposes
credentials, session tokens and chat content to interception or modification; use only on trusted networks.
