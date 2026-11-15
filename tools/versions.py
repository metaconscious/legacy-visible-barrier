"""Release identifiers shared by verification and publication."""

import re

_NUMBER = r"(?:0|[1-9][0-9]*)"
_PRE_ID = r"(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
SEMVER = re.compile(
    rf"{_NUMBER}\.{_NUMBER}\.{_NUMBER}"
    rf"(?:-(?P<prerelease>{_PRE_ID}(?:\.{_PRE_ID})*))?"
    r"(?:\+(?P<build>[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
)
TARGETS = {
    "legacy-fabric": {"name": "Legacy Fabric"},
    "ornithe": {"name": "Ornithe"},
}
GAME_VERSION = "1.8.9"


def parse_version(version):
    match = SEMVER.fullmatch(version)
    if not match:
        raise ValueError(f"Invalid SemVer 2.0.0 version: {version!r}")
    return match.groupdict()


def artifact_version(base, target):
    parsed = parse_version(base)
    if parsed["build"] or target not in TARGETS:
        raise ValueError("The release version must have no build metadata and the target must be supported")
    return f"{base}+{target}"


def validate_tag(tag, base):
    artifact_version(base, "legacy-fabric")
    if tag != f"v{base}":
        raise ValueError(f"Tag must equal v{base}")


def changelog_entry(path, base):
    text = path.read_text(encoding="utf-8")
    pattern = rf"^## \[{re.escape(base)}\](?:[^\n]*)\n(.*?)(?=^## |\Z)"
    match = re.search(pattern, text, re.MULTILINE | re.DOTALL)
    if not match or not match[1].strip():
        raise ValueError(f"CHANGELOG.md must contain a nonempty entry for {base}")
    return match[1].strip()
