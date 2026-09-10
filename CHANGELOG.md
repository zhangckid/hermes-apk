# Changelog

## 1.0.0 — Unreleased (first public version)

The public version sequence starts at 1.0.0. Earlier 1.1–1.4 builds were private development versions;
Android versionCode continues at 19 so existing installations can upgrade without clearing data.

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
