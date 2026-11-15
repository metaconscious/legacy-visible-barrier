"""Release identifiers shared by verification and publication."""

import json
import re
from pathlib import Path

_NUMBER = r"(?:0|[1-9][0-9]*)"
_PRE_ID = r"(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
SEMVER = re.compile(
    rf"{_NUMBER}\.{_NUMBER}\.{_NUMBER}"
    rf"(?:-(?P<prerelease>{_PRE_ID}(?:\.{_PRE_ID})*))?"
    r"(?:\+(?P<build>[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
)
TARGETS = json.loads((Path(__file__).resolve().parents[1] / "gradle/targets.json").read_text(encoding="utf-8"))
PROJECT_ID = TARGETS["legacy-fabric"]["modrinth_project"]
GAME_VERSION = "1.8.9"


def runtime_dependencies(target):
    info = TARGETS[target]
    dependencies = {"fabricloader": ">=" + info["loader_minimum"], "minecraft": GAME_VERSION, "java": ">=8"}
    dependencies.update({("osl-" + module if info["platform"] == "ornithe" else module):
                         ">=" + (version.split("+")[0] if info["platform"] == "ornithe" else version)
                         for module, version in info["api_modules"].items()})
    return dependencies


def target_metadata(target):
    info = TARGETS[target]
    return {"id": target, "name": info["name"], "sentinels": info["sentinels"]}


def target_paths(values):
    result = {}
    for value in values:
        target, separator, path = value.partition("=")
        if not separator or not path or target not in TARGETS or target in result:
            raise ValueError(f"Expected a distinct known target and path: {value}")
        result[target] = Path(path)
    return result


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


def release_channel(base):
    prerelease = parse_version(base)["prerelease"]
    if not prerelease:
        return "release"
    return "alpha" if prerelease.split(".")[0] == "alpha" else "beta"


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
