# Live service testing

`HermesClientLiveTest` is an explicit opt-in integration test using the production
HTTP client, cookie jar, PTY socket and terminal control responses under
Robolectric (API 35). Credential storage is in-memory; this is not an installed
APK or a physical-device UI test.

## Run

Only run against a server you are authorized to test. The test uploads a small
generated PNG and can create conversations and make paid model requests. Messages
are prefixed with `CATGO_LIVE_HTTP_` or `CATGO_LIVE_HTTPS_`. It does not delete
conversations or uploads afterward.

Set `CATGO_LIVE_HTTP` and/or `CATGO_LIVE_HTTPS` to the desired base URLs, and
`CATGO_LIVE_USER` to the test username. In Bash, read the password without echo:

```bash
read -r -s -p 'Test password: ' CATGO_LIVE_PASSWORD
export CATGO_LIVE_PASSWORD
./gradlew --no-daemon --offline --no-build-cache --rerun-tasks \
  :app:testDebugUnitTest --tests '*HermesClientLiveTest' --info
unset CATGO_LIVE_PASSWORD
```

Provide the normal project JDK and Android SDK environment. Do not place passwords
in source files, command arguments or committed logs. Tests without their endpoint
environment variable are skipped. `--rerun-tasks --no-build-cache` prevents cached
results from being mistaken for a new live run.

The test honors `HTTP_PROXY` / `HTTPS_PROXY`. To compare direct connectivity,
unset proxy variables in a dedicated shell before starting Gradle. A proxy-path
failure alone is not proof of a device or server defect.

## Browser reference

The optional `scripts/test_web_reconnect.py` uses Playwright with a local Chrome
binary. Install Playwright in an isolated virtual environment, set `CATGO_WEB_BASE`
and `CATGO_LIVE_USER`, then run the script with that environment\'s Python. Override
`CATGO_CHROME` if Chrome is not at `/usr/bin/google-chrome`. The password is requested
without echo. No browser profile, cookies or traces are exported. The script creates
a new marked conversation and simulates a WebSocket interruption (1001); normal
traffic is forwarded to the real deployment. It makes two model requests and verifies
both replies through REST history. It is a direct-connection test.

## Coverage and limits

- Login followed by authenticated identity verification.
- Recreating the client with saved cookies/credentials and reading protected APIs.
- Session, model and reasoning metadata reads (not changing model configuration).
- PNG upload (not proof of model image understanding).
- Terminal startup, marked text submission and an assistant reply saved to history.
- Closing and reopening the socket, followed by a second saved reply.

The terminal responds to fixed cursor/status queries, as the App does. Diagnostics
only report stage, counts, booleans and recognized connection-error categories;
they do not dump credentials, socket tickets or terminal contents. Matching reply
text in terminal output alone is insufficient because input echo can contain it.

## Fixed in 1.0.2

Both direct HTTP and HTTPS reconnect/second-reply tests now pass. A real Chrome
web-client reference test also passed. See [the cause, changes and evidence](VERIFICATION-1.0.2.md).
The observations below are retained as pre-fix diagnostic history, not current results.

## Pre-fix observations: 2026-09-15

Tested the user-provided HTTP port 19119 and HTTPS port 29119 on the same host,
using the working tree based on version 1.0.1 (build 21).

On the test-machine proxy path, both origins passed login, restored-client
authentication, protected metadata reads, PNG upload and terminal startup.
HTTP then reported a missing WebSocket pong and disconnected; no marked session
or saved reply was found before the 110-second reply deadline.

HTTPS successfully created a marked session and saved its first assistant reply.
The reconnect test kept an open socket but did not find the expected second saved
assistant reply before the deadline. This does not establish the root cause.

An unauthenticated direct request to `/chat` returned HTTP 302 on both origins,
confirming that direct connectivity is available from this test machine.
The early test attempts lacked the App's terminal control replies and must not be
used as evidence that both servers cannot chat.

A direct HTTP run (proxy environment variables unset) passed login, restored-client
reads, PNG upload, marked new-session creation and the first saved assistant reply.
Its reconnect phase kept the socket open and echoed input, but did not find the
second expected saved assistant reply within 110 seconds either. Both completed
end-to-end test cases therefore failed at reconnect reply verification, despite
successful initial chatting (HTTP direct, HTTPS via proxy). The cause remains
unresolved; an echoed expected phrase is not a verified model response.

Thus the proxy-path missing-pong failure was not reproduced during direct initial
chat. This does not establish the cause of a particular phone\'s connection failure.

Physical-device HTTP login, the server-settings button, voice, image understanding
and background/lock-screen reconnection still require separate verification.
