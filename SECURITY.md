# Security

This is an experimental client. There is no security audit certification or guaranteed response SLA.
Only the current development version is intended to receive fixes; older APKs are historical artifacts.

## Reporting a vulnerability

Once the public repository is created, maintainers should enable GitHub private vulnerability reporting.
Use its **Security → Report a vulnerability** flow when available. If it is unavailable, open an issue
asking for a private contact channel **without publishing vulnerability details or credentials**.
No maintainer email or reporting endpoint has been established yet.

Include the app version, Android version, affected component, expected impact, and a minimal sanitized
reproduction. Never attach real passwords, cookies, WebSocket tickets, private keys, audio, or full chat logs.

## Trust boundaries

- The app sends text and selected images to the HTTPS server chosen by the user. That server and its
  model providers have their own data retention and command execution policies.
- Login secrets and cookies use Android Keystore-backed encryption; host, port and username are regular
  app preferences. Backups and cleartext network traffic are disabled in the manifest.
- The bundled intermediate certificates assist chain building; platform trust, expiry and hostname
  checks remain active. This is not arbitrary trust of self-signed certificates or exact Chrome behavior.
- API redirects are disabled. Configure the final server origin rather than a redirecting URL.
- Hermes questions/approvals are parsed from known PTY panels, not cryptographically authenticated
  structured authorization events. Read the displayed command. Unsupported or truncated requests fail closed.
- Password prompts use a separate masked field, are excluded from chat history, and enable screenshot
  protection while displayed. The server may have its own password caching behavior.
- Offline recognition runs locally after a model download. Explicitly selected system recognition may
  use a network service. TTS also depends on the chosen system engine.
- SSH has been removed. Upgrade cleanup clears the retired SSH preferences without deleting the key
  shared by encrypted chat credentials.

Use a dedicated, least-privileged server account. Do not expose this experimental client/backend as an
unrestricted public command execution service. See [known risks and review](docs/REVIEW.md).
