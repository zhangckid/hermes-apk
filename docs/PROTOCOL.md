# Backend compatibility

This app is an unofficial client for a **Hermes web deployment exposing the routes below**. Installing
Hermes CLI alone does not imply these web endpoints exist. This is not an OpenAI-compatible API client.
No app-specific server plugin, messaging channel or proxy is required for an already accessible compatible web deployment.
The Android client does not add server-side multi-user permissions; account sharing and isolation remain server responsibilities.
No universal compatibility with all Hermes deployments is claimed. Before public launch, record the
tested web backend repository/revision and deployment requirements.

| Transport | Route | Purpose |
| --- | --- | --- |
| POST | `/auth/password-login` | Username/password authentication |
| GET | `/api/auth/me` | Verify the current authenticated session |
| POST | `/api/auth/ws-ticket` | Obtain a WebSocket ticket |
| WSS / WS | `/api/pty` | Conversation input, status and interactive prompts |
| GET | `/api/sessions`, `/api/sessions/search` | Session list/search |
| GET | `/api/sessions/{id}/messages` | Final structured history |
| POST | `/api/chat/image-upload` | Upload selected images |
| GET | `/api/model/info`, `/api/model/options` | Model information/options |
| POST | `/api/model/set` | Change the server model |
| GET/PUT | `/api/config` | Read/update reasoning configuration |
| POST | `/auth/logout` | End the login session |

The final HTTP/HTTPS origin must be entered directly. HTTPS is the default; HTTP is explicit opt-in with
an unencrypted-transport warning. WebSockets use WSS for HTTPS and WS for HTTP. The selected protocol is
persisted; legacy configurations default to HTTPS. A pasted URL must match the selected protocol. Redirects are not followed. A same-origin 302/303 response from the password-login endpoint may establish a cookie;
the client then verifies `/api/auth/me` directly before reporting success. Password bodies are never forwarded to a redirect target. Host/path pasted from a
web URL is normalized to the origin; the separate port field takes precedence. URL credentials and
query/fragment parameters are rejected. Cookies and WebSocket tickets must not be logged. Secure cookies are not downgraded for HTTP servers.

Model/reasoning changes target the server's `default` profile and may affect other clients using it.
Expensive models require explicit confirmation. This is not a per-device-only preference.

## Reconnect identity

Keep the original PTY `channel`, `attach`, and `resume` target for transport retries.
A newly created conversation initially has no `resume` parameter; learning its database
session ID does **not** change that launch target. Changing it can start another CLI
owner and produce a live-owner conflict. `fresh=1` is sent only on the first attempt
for a new attachment. Distinct conversations receive independent attachment IDs.

A `{"type":"resume","id":"..."}` WebSocket frame announces terminal replay. Its
ID is not necessarily an `/api/sessions` ID and must not replace the selected chat
history ID. The app discovers and binds persisted session IDs through the REST
session list and message history. See [live verification](VERIFICATION-1.0.2.md).

The PTY implementation currently recognizes known boxed Hermes question, command approval and secret
panels. Numeric selections map to actual server keys; ordinary answers are single-line. Truncated,
expired, incomplete or unsupported panels are not safe to authorize. Independent PTY processes do not
automatically share pending prompts across devices.

Protocol fixtures and tests are under `app/src/test/`. `scripts/hermes-prompt-fixture.py` regenerates
prompt_toolkit rendering fixtures; normal Android tests use the committed fixture and need no Python packages.

Upstream context: [Hermes CLI](https://github.com/NousResearch/hermes-agent),
[question/approval handling](https://github.com/NousResearch/hermes-agent/blob/main/hermes_cli/cli_tui_mixin.py),
[secret panels](https://github.com/NousResearch/hermes-agent/blob/main/hermes_cli/cli_modal_mixin.py).
