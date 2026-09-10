# Third-party components and asset provenance

Original project code and documentation are licensed under [MIT](LICENSE). Third-party components
retain their own licenses. The supplied artwork is excluded from the project's MIT grant until its rights
are confirmed. This inventory is a release-review aid, not a completed legal audit.

| Component | Source / notice | Use |
| --- | --- | --- |
| AndroidX / Jetpack Compose | [AndroidX](https://android.googlesource.com/platform/frameworks/support/) | UI, lifecycle, app compatibility |
| Kotlin and kotlinx | [Kotlin](https://github.com/JetBrains/kotlin), [coroutines](https://github.com/Kotlin/kotlinx.coroutines), [serialization](https://github.com/Kotlin/kotlinx.serialization) | Language/runtime/data |
| OkHttp / Okio | [OkHttp](https://github.com/square/okhttp), [Okio](https://github.com/square/okio) | HTTPS/WebSocket transport |
| Coil | [Coil](https://github.com/coil-kt/coil) | Image loading |
| Apache Commons Compress | [project](https://commons.apache.org/proper/commons-compress/) | Offline model archive extraction |
| sherpa-onnx 1.13.2 | [release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.2), [bundled license](app/libs/sherpa-onnx-LICENSE.txt) | Native offline speech |
| Gradle wrapper 8.13 | [Gradle](https://github.com/gradle/gradle) | Build bootstrap |
| JUnit, Robolectric, MockWebServer | Declared in `app/build.gradle.kts` | Local tests, not app runtime |

Exact direct versions are declared in the Gradle files. Resolve `debugRuntimeClasspath`/`releaseRuntimeClasspath`
to audit transitive dependencies for a release. The old JSch, Apache MINA SSH test dependency, xterm assets
and terminal font have been removed.

## Vendored speech binary

`app/libs/sherpa-onnx-1.13.2.aar` SHA-256:
`aa5505c0ec4f8bdaee5f214a64ba3012be64f2aecc022e82a64f33392b8dd245`

It contains native sherpa-onnx and ONNX Runtime libraries. The checked-in top-level license is retained;
before redistribution, verify provenance against the upstream release and collect the exact bundled
native/transitive notices. A local checksum identifies the file but does not establish upstream provenance.

## Downloaded model

Offline recognition uses `sherpa-onnx-paraformer-zh-small-2024-03-09` from the upstream
[ASR model release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models).
The archive is checked against a fixed SHA-256 in `OfflineVoiceInput.kt`. Weights are not included in
the source tree or APK. Verify the model's original license and applicable attribution before public launch;
do not infer model-weight rights from the inference engine's license.

## Artwork and certificates

- `app/src/main/res/drawable-nodpi/catgo_icon.png` was supplied during private development. Authorship and
  public redistribution rights are not yet established here. Keep it out of a public release until confirmed.
- `app/src/main/res/raw/hermes_ca_intermediates.pem` contains public intermediate certificates used for
  certificate-chain completion, not a private key or a user credential. Platform trust validation is retained.
