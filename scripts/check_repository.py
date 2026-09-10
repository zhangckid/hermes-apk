#!/usr/bin/env python3
"""Offline checks for intended source files. Not a replacement for a secret/license audit."""
from pathlib import Path
import hashlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = ["README.md", "CONTRIBUTING.md", "SECURITY.md", "CODE_OF_CONDUCT.md",
            "CHANGELOG.md", "THIRD_PARTY_NOTICES.md", "docs/RELEASE_CHECKLIST.md",
            ".github/workflows/android.yml"]
CHECKSUMS = {
    "app/libs/sherpa-onnx-1.13.2.aar": "aa5505c0ec4f8bdaee5f214a64ba3012be64f2aecc022e82a64f33392b8dd245",
    "gradle/wrapper/gradle-wrapper.jar": "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f",
}
errors = []
for name in REQUIRED:
    if not (ROOT / name).is_file():
        errors.append("Missing required document/config: " + name)
for name, expected in CHECKSUMS.items():
    path = ROOT / name
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        errors.append("Vendored binary checksum mismatch: " + name)

sources = []
for name in ("app/src", "app/libs", "gradle", "scripts", "docs", ".github"):
    sources.extend(p for p in (ROOT / name).rglob("*") if p.is_file())
sources.extend(p for p in ROOT.iterdir() if p.is_file() and p.suffix in (".md", ".kts", ".properties"))
sources.extend(ROOT / name for name in (".gitignore", ".gitattributes", ".editorconfig", "app/build.gradle.kts", "app/proguard-rules.pro"))

for path in sources:
    name = path.relative_to(ROOT).as_posix()
    if path.is_symlink():
        errors.append("Source symlink requires manual review: " + name)
        continue
    if path.suffix in (".apk", ".aab", ".jks", ".keystore", ".p12", ".pfx"):
        errors.append("Private/build artifact in source directories: " + name)
    if path.suffix in (".aar", ".jar", ".png", ".pyc"):
        continue
    text = path.read_text(encoding="utf-8", errors="replace")
    # Report paths only: never echo a matching secret into CI logs.
    if re.search(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", text):
        errors.append("Possible private key: " + name)
    if name != "scripts/check_repository.py" and re.search(r"/home/[^/\s]+/|[A-Za-z]:\\Users\\", text):
        errors.append("Machine-specific path: " + name)
    if path.suffix == ".xml":
        try:
            ET.fromstring(text)
        except ET.ParseError:
            errors.append("Malformed XML: " + name)
    if name.startswith("app/src/main/") and path.suffix in (".kt", ".xml"):
        if "win.catgo.gpt.ssh" in text or "SshViewModel" in text or "onSsh" in text:
            errors.append("Retired SSH implementation reference: " + name)
    if path.suffix == ".md" and not name.startswith("docs/history/"):
        for target in re.findall(r"\]\(([^\s)]+)\)", text):
            if "://" in target or target.startswith(("#", "mailto:")):
                continue
            local = target.split("#", 1)[0]
            if local and not (path.parent / local).exists():
                errors.append("Broken local documentation link: " + name + " -> " + local)

for retired in ("app/src/main/java/win/catgo/gpt/ssh", "app/src/test/java/win/catgo/gpt/ssh",
                "app/src/main/assets/terminal", "app/src/main/java/win/catgo/gpt/ui/HermesInteractionDialog.kt"):
    if (ROOT / retired).exists():
        errors.append("Retired module still in source tree: " + retired)
build = (ROOT / "app/build.gradle.kts").read_text()
if "com.github.mwiede:jsch" in build or "org.apache.sshd" in build:
    errors.append("Retired SSH dependency still declared")

if errors:
    print("Repository checks failed:")
    print("\n".join("- " + error for error in sorted(set(errors))))
    sys.exit(1)
print("PASS: source hygiene, XML, documentation links, vendored checksums and SSH removal")
if not (ROOT / "LICENSE").exists():
    print("RELEASE BLOCKER: project license has not been selected by the owner")
print("Manual release gates: artwork rights, native/model notices, backend revision, device tests and signing")
