# 1.0.2 reconnect verification — 2026-09-15

Android version code: 22.

## Cause and web-client comparison

The deployed web frontend was inspected directly (`ChatPage-BDvFonGW.js`). It
keeps the original launch target and channel during transport retries, sends
`fresh` on the initial attempt only, and treats a `type: resume` frame as terminal
replay metadata rather than navigating chat history to its `id`.

The previous Android implementation changed the launch target from no resume ID
to a newly discovered database session ID. A live test captured the server error
`already has a live owner`: the original CLI was still running, but the retry
attempted to resume it as another owner. Keeping only the channel stable was
tested separately and was insufficient.

After retaining the original target, the server replayed the original PTY. Its
short replay ID exposed a second bug: Android replaced the database chat ID with
that terminal ID. Replay notifications are now a separate callback. Session
binding comes from structured REST discovery, retaining the corresponding
attachment for later navigation. Stale discovery results cannot bind after the
user switches conversations.

## Live results

| Check | Android client / HTTP direct | Android client / HTTPS direct | Chrome web / HTTPS |
| --- | --- | --- | --- |
| Authenticate | Passed | Passed | Passed |
| Saved-client authentication and metadata reads | Passed | Passed | Not separately tested |
| PNG upload | Passed | Passed | Not tested |
| New conversation and first persisted assistant reply | Passed | Passed | Passed |
| Disconnect, reconnect and second persisted assistant reply | Passed | Passed | Passed |
| Same database session; exactly two marked user messages | Passed | Passed | Passed |

Android's real production HTTP/PTY code was exercised under Robolectric API 35,
using in-memory credential storage and direct connections to the authorized
deployment. Both end-to-end tests passed in the same run. This is not a physical
phone test.

Chrome ran the deployed web UI in an isolated headless context. Input was typed
into its actual xterm textarea. A WebSocket test route sent close code 1001 to
exercise the page's automatic reconnect path while forwarding normal traffic to
the real service. The channel stayed stable and the second reply was verified in
REST history. A clean `close()` is intentionally treated as session end by this
web frontend, so it is not an equivalent automatic-reconnect test.

Test conversations have `CATGO_LIVE_` or `CATGO_WEB_` prefixes. They and uploaded
test images were left on the server; no user conversations were deleted. No
server settings, plugins, model selection, or credentials were changed.

## Regression scope

Local regressions cover stable channels, original resume targets, first-attempt
fresh semantics, independent conversations, account-state reset, and replay
metadata separated from terminal output. Existing login, navigation, input and
permission-prompt tests remain part of the full suite.

Image upload is not proof of vision-model understanding. Android process death,
device lock-screen behavior, voice and another client's already-owned historical
session are not claimed as verified by these tests. The app does not kill another
CLI owner or bypass server session locks.

For opt-in commands and credentials handling, see [live service testing](LIVE-SERVICE-TESTING.md).

## Build and artifact

The final full local suite passed: 128 tests, 125 passed, 3 opt-in live tests
skipped. The two new live tests were separately enabled and both passed. Debug
APK, unsigned Release APK and Android lint tasks all completed successfully.
Repository hygiene and Python script syntax checks also passed.

Artifact: `catgo-gpt-v1.0.2-build22-debug.apk` (local, excluded from Git).
Version and signing were checked; its debug signing certificate matches build 21.

SHA-256:

```text
bacaa37e09e7eb4f8041a46535066899386c93915c129da01384dd32ee3d3a2c
```
