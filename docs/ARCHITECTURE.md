# Architecture

catgo-gpt is a single Android application module using Kotlin and Jetpack Compose.

```text
Compose screens and voice dialog
          ↓ user events        ↑ AppUiState
AppViewModel (conversation ownership, reconnect, orchestration)
          ├─ HermesClient → HTTPS auth/history/images/model settings
          ├─ HermesPtySocket → WSS input/readiness/status/prompt bytes
          ├─ presentation and prompt policies → visible messages/actions
          └─ settings + encrypted cookie/credential stores
```

## Boundaries

- `ui/`: Compose views, ViewModel, presentation policies, voice input and TTS lifecycle.
- `network/`: HTTP/WebSocket transport, incremental UTF-8/ANSI processing, prompt parsing and input leases.
- `model/`: protocol data classes and server/model configuration.
- `data/`: preferences, encrypted state, upgrade cleanup.
- `i18n/` and `res/values*`: English/Chinese application text. Server content is not translated.

HTTP message history is the authoritative source of final messages. PTY output supplies readiness,
compact activity status and interactive prompts; it is not displayed as a general terminal.
`TerminalTextScreen` is a bounded parser for known prompt layouts, not the removed SSH terminal emulator.

Every socket callback and pending send must belong to the current conversation/socket generation.
Prompt leases also check screen revision and expiry. A successful transport send is not a server
acknowledgment, and approvals are never replayed automatically after reconnect.

Normal question replies use the chat composer; secrets use a separate field. Transient prompt exchange
history is memory-only, capped and scoped by session; it is not written back to server history.

Offline speech is the default when opening the voice dialog. System recognition is created only after
explicit selection. Closing or switching the dialog cancels recording/download work. Speech output is a
bounded extractive summary, while the on-screen response stays detailed.

## Current limitations

`AppViewModel` still coordinates many concerns. Future refactoring should extract conversation and
reconnect orchestration behind tested interfaces, not rewrite transport and UI simultaneously.
PTY parsing is backend-version-sensitive. The tests do not replace device tests, microphone tests,
or end-to-end verification against an explicitly supported server revision.
