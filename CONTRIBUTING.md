# Contributing

Thanks for helping improve catgo-gpt. Chinese and English contributions are welcome.
The project is preparing for its first public release; see [release prerequisites](docs/RELEASE_CHECKLIST.md).

## Before changing code

- Read [architecture](docs/ARCHITECTURE.md) and [protocol constraints](docs/PROTOCOL.md).
- Discuss substantial changes in an issue before implementing them. SSH was intentionally removed before the public 1.0.0 release.
- Never include credentials, private server addresses, conversation exports, microphone recordings, or signing keys.
- Check that you have permission to contribute code and assets. Original contributions are made under the project's [MIT license](LICENSE).

## Development workflow

### Git commits

Use Conventional Commit messages: `type(scope): short imperative description`.
Types include `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci` and `chore`.
Scopes identify modules, for example `network`, `data`, `speech` or `ui`.
Keep commits focused and include related regression tests with behavior changes.
Explain motivation and verification in the body when useful. Never commit credentials,
build outputs, local settings or signing keys.

The initial 1.0.0 import is grouped by module, not reconstructed historical changes.
Intermediate import commits are not standalone releases; the complete import is the
verified baseline. Subsequent commits should remain buildable and testable.

### Checks

1. Install JDK 17 and Android SDK 36 as described in [development](docs/DEVELOPMENT.md).
2. Keep changes small and focused. Add regression tests for bug fixes.
3. Run `python3 scripts/check_repository.py` and `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
4. For production code changes, also run `./gradlew :app:assembleRelease` to check R8 compatibility.
5. Explain user-visible behavior, test coverage and untested device behavior in the pull request.

Use Kotlin conventions and Compose's unidirectional state flow. Keep protocol parsing and policy decisions
testable without UI. Preserve cancellation and conversation ownership checks. Do not automatically retry
commands, answers, password submissions, or authorization decisions.

UI text needs both English and Chinese resources and a matching `UiText` mapping. Speech must not switch
to a system/network-backed recognizer without the user's choice. Do not weaken certificate validation to
work around server configuration errors.

Tests normally use local fixtures and loopback servers. Live tests require explicit opt-in, create real
conversations and may incur model charges; never run them using someone else's account.

For security issues, use [SECURITY.md](SECURITY.md), not a public reproduction containing secrets.
