# Changelog

## 1.0.1 — 2026-09-15

- Keep server settings open when delayed startup/login requests finish; cancel old authentication work and clear busy/model-dialog state.
- Verify authenticated sessions before completing login. Support same-origin 302/303 login responses without forwarding passwords.
- Report HTTPS-only cookies and unsupported redirects; retain login errors on screen and allow cancelling a pending connection.
- Bound HTTP requests and report reconnect failures after the existing ten-second quiet period.
- Exercise real HermesClient HTTP login, persisted cookies, session listing and WS connection against a local fixture; add delayed-navigation and settings-button regressions.
- Android versionCode 21; existing encrypted credentials remain compatible.


## 1.0.0 — Unreleased (first public version)

The public version sequence starts at 1.0.0. Earlier 1.1–1.4 builds were private development versions;
Android versionCode continues at 20 so existing installations can upgrade without clearing data.

- Add saved HTTP/HTTPS selection, matching WS/WSS transport, cleartext warnings and exact-origin request restrictions; HTTPS remains the default. App-level cleartext permission is enabled explicitly for user-defined HTTP hosts.
- Adopt MIT for original project code and documentation.
- Show `hermes-agent.nousresearch.com` as a grey host placeholder, without filling or connecting automatically.
- Default new installations to English; preserve explicit Chinese/English selections and saved server settings.
- Add project motivation, non-official status and real Compose UI screenshots to the README.


### Removed

- Entire SSH debug feature, navigation, implementation, JSch dependency, terminal WebView assets and dedicated tests.
- Unused pre-1.3 Hermes terminal dialog. Inline Hermes questions and approvals remain.

### Changed

- Retired SSH configuration and encrypted credentials are cleared on app startup; chat configuration remains.
- Server address parsing supports IPv6 and rejects HTTP, URL credentials, queries and fragments.
- API clients no longer automatically follow redirects, including HTTPS-to-HTTP redirects.
- README and contributor/security/development documentation reorganized for a new public repository.
- Added repository hygiene checks, issue/PR templates, and a read-only GitHub Actions build workflow.

### Earlier versions

See [historical release notes](docs/history/README.md). They describe their respective releases, not current
features or current test results. The old SSH notes are retained solely as historical context.
