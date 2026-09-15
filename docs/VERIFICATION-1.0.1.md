# 1.0.1 verification — 2026-09-15

Version name 1.0.1, Android version code 21.

## Fixes and evidence

- Startup session restoration previously ran independently of server-settings navigation. Its late
  `loadHome` update could select chat again. Authentication work is now cancelled and versioned;
  the settings action also clears busy and model-dialog state.
- Local integration tests cover a delayed restore completing after settings opens, cancelling a
  non-responsive manual login, and a failed restore returning to a visible login error.
- Compose tests click the actual drawer settings button and exercise the cancel-connection action.
- Real HermesClient tests against local MockWebServer cover HTTP login (200 and same-origin 302/303),
  session validation, cookie persistence across client recreation, session listing and WS connection.
- Secure-only cookies over HTTP and cross-origin login redirects produce errors. Redirect targets
  never receive the password. A successful login POST without an authenticated session is rejected.
- Login errors remain on the configuration screen. Requests are bounded and reconnect failures are
  shown after the existing ten-second quiet period; old errors are cleared when changing conversations.

## Verification

The final full unit/UI test suite, debug APK build, unsigned release build and Android lint tasks all
completed successfully. Tests use local fixtures; the optional live-service test remains disabled.
No physical-device or live HTTP deployment verification was performed.

The user's HTTPS endpoint works, but no actual HTTP address/port was supplied. These tests establish
client compatibility with the exercised HTTP protocol, not whether that deployment exposes an HTTP
listener or supports non-Secure login cookies. Such deployments must continue to use HTTPS or provide
a compatible HTTP endpoint; the app does not change server configuration.

Debug APK: `catgo-gpt-v1.0.1-build21-debug.apk` (local artifact, excluded from Git).

SHA-256:

```text
992006b7928a7a0971e6d0e9a83176ddf6683ab20df4dbca4d4214c2ee9a657e
```
