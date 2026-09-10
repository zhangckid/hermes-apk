# Code review — private 1.4.0 preparation

Historical verification record: the subsequent public version has been renamed 1.0.0 (versionCode 19).
MIT has since been selected; see the current README and release checklist. Results below describe the earlier build.

Reviewed on 2026-09-10. Scope: SSH removal boundaries, credential storage/migration, server address
handling, HTTP/TLS policy, prompt/session ownership, speech lifecycle, build inputs and repository hygiene.
This is a focused engineering review, not an independent security audit or full backend certification.

## Findings addressed

| Finding | Change | Evidence |
| --- | --- | --- |
| SSH remained unreliable and coupled display failure to connection teardown | Removed the feature rather than retaining another partially working input path | SSH source/assets/dependency absence check; remaining chat UI tests |
| Unused Hermes terminal dialog kept the removed module reachable in source | Removed the old dialog, legacy state fields and related tests | Compilation and repository checks |
| Removing UI alone would leave stored SSH secrets behind | Clear only retired SSH preference namespaces at startup | Migration tests on SDK 28 and 35, preserving chat preference/credential namespaces |
| Host parsing split at the first colon, breaking IPv6, and accepted HTTP/userinfo-like input | Parse through HttpUrl, require HTTPS, reject userinfo/query/fragment/backslash, retain explicit port field | ServerConfig tests |
| Default HTTP redirect behavior could leave the selected origin or downgrade HTTPS | Disable API redirects and TLS redirects | Local HTTP 301/302/303/307/308 tests verify no forwarding to a second server |
| Private deployment hostname appeared in input examples and tests | Replace with reserved example domains | Source hygiene/manual scan |
| Root README mixed obsolete behaviors with current features | Rewrite onboarding; archive historical release documents; add dedicated protocol/development/security docs | Local Markdown link check |
| Build-tools version was implicit and disagreed with onboarding | Pin build-tools 36.0.0 and add the Gradle distribution checksum | Fresh SDK reconstruction and build |
| APKs, caches and local backups were easy to accidentally stage | Expand ignore rules and add source hygiene and vendored integrity checks | Repository checker; staged Git review still required |

## Preserved safeguards

Platform CA/hostname/expiry validation remains in place. Prompt input still requires a current lease,
connection and screen revision; incomplete/truncated authorization does not become an automatic approval.
Passwords are excluded from prompt history. Voice continues to default offline and only selects a system
recognizer after user interaction. Cancellation and conversation generation checks were retained.

## Open risks / follow-up

1. **Release blockers:** project license, supplied artwork rights, native binary/model notices, supported
   web backend revision, private reporting contact and production signing remain owner decisions.
2. **Protocol:** known CLI-panel parsing is not a stable structured authorization API. Changes upstream,
   especially long/partial panels, require fresh fixtures and real backend tests.
3. **Device behavior:** no physical Android/Gboard/audio device is attached. Local tests cannot certify
   keyboard resize, microphone quality, OEM lifecycle or installed TTS engines.
4. **Maintenance:** AppViewModel remains large. Extract orchestration incrementally behind regression
   tests; a broad architecture rewrite was not required to remove SSH safely.
5. **Server integration:** redirects now fail instead of following automatically. Deployments requiring
   redirect-based login must expose a direct API endpoint or receive a separately reviewed same-origin flow.
6. **Credential audit:** checked source/examples do not substitute for scanning the actual Git index and
   history. There is not yet a Git repository to audit or a GitHub workflow execution to inspect.

## Verification status

Completed on 2026-09-10 using restored JDK 17, Android platform 36 and pinned build-tools 36.0.0.

- 96 JVM/Android-framework cases: **95 passed, 0 failed, 1 live-service case skipped**.
- Debug and unsigned Release builds succeeded, including R8; Android lint: **0 errors, 30 warnings, 1 hint**.
- Remaining warnings include dependency updates, API annotations, monochrome icon support and KTX
  style suggestions. They were not hidden with blanket suppressions or treated as a completed cleanup.
- Source hygiene, XML/local documentation links, vendored binary hashes and SSH absence checks passed.
- GitHub YAML parsed successfully; workflow permissions, triggers and action commit pins were checked locally.
  The workflow has not yet run on GitHub.
- Debug APK DEX inspection found no SSH implementation/JSch classes or old interaction dialog.
  Debug and Release APKs contain no bundled terminal assets.
- Debug APK version is 1.4.0 (versionCode 18); its signature matches the existing 1.3.x debug builds.
- APK file: `catgo-gpt-v1.4.0-debug.apk`.
  SHA-256: `c2ab2e1336c6060e05476e1a36b84d4941f4941a3c4a961172a460b1b94883de`.

No remote Hermes service, physical phone, live microphone or GitHub repository was used for these results.
Historical browser/SSH tests were removed along with the feature; their former counts are not claimed as
coverage for this revision. Deleted source/assets/tests are recoverable in the ignored local backup
directory; historical APKs were retained locally and are excluded from source control.
