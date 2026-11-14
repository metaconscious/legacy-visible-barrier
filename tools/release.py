"""Stage verified distributions, or publish them through Modrinth and GitHub CLI."""

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import urllib.error
import urllib.request
import uuid
from pathlib import Path

from versions import (GAME_VERSION, PROJECT_ID, TARGETS, artifact_version, changelog_entry,
                      release_channel, validate_tag)

ROOT = Path(__file__).resolve().parent.parent
REPOSITORY = "metaconscious/legacy-visible-barrier"


def digest(path):
    return hashlib.sha512(path.read_bytes()).hexdigest()


def stage(base, report_path, jars, output, tag=None):
    if tag:
        validate_tag(tag, base)
    status = subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True)
    if status.strip():
        raise ValueError("Release staging requires a clean working tree")
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    changelog = changelog_entry(ROOT / "CHANGELOG.md", base)
    reports = {r["target"]: r for r in json.loads(report_path.read_text(encoding="utf-8"))}
    if set(reports) != set(TARGETS) or set(jars) != set(TARGETS):
        raise ValueError("Both distributions must pass artifact verification")
    output.mkdir(parents=True, exist_ok=True)
    versions = []
    for target, info in TARGETS.items():
        jar, report = jars[target], reports[target]
        version = artifact_version(base, target)
        if report["version"] != version or jar.name != report["filename"] or digest(jar) != report["sha512"]:
            raise ValueError(f"Artifact changed after verification: {target}")
        shutil.copyfile(jar, output / jar.name)
        versions.append({
            "target": target, "filename": jar.name, "sha512": report["sha512"], "size": report["size"],
            "metadata": {
                "project_id": PROJECT_ID, "name": f"{base} — {info['name']}", "version_number": version,
                "version_type": release_channel(base), "game_versions": [GAME_VERSION], "loaders": [target],
                "changelog": changelog, "featured": False,
                "dependencies": [{"project_id": info["dependency"], "dependency_type": "required"}],
            },
        })
    manifest = {"schema": 1, "repository": REPOSITORY, "version": base, "tag": f"v{base}",
                "commit": commit, "changelog": changelog, "versions": versions}
    (output / "release.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output / "SHA512SUMS").write_text(
        "".join(f"{v['sha512']}  {v['filename']}\n" for v in versions), encoding="utf-8")
    print(f"Staged {base} from {commit} in {output}")


def load_manifest(path):
    manifest = json.loads(path.read_text(encoding="utf-8"))
    validate_tag(manifest["tag"], manifest["version"])
    if manifest["schema"] != 1 or manifest["repository"] != REPOSITORY:
        raise ValueError("Unexpected release manifest")
    records = manifest["versions"]
    if len(records) != 2 or {v["target"] for v in records} != set(TARGETS):
        raise ValueError("A release must contain exactly one artifact per target")
    for record in records:
        target, metadata = record["target"], record["metadata"]
        version = artifact_version(manifest["version"], target)
        if record["filename"] != f"legacy-visible-barrier-{version}.jar":
            raise ValueError("Unexpected distribution filename")
        expected = {
            "project_id": PROJECT_ID, "name": f"{manifest['version']} — {TARGETS[target]['name']}",
            "version_number": version, "version_type": release_channel(manifest["version"]),
            "game_versions": [GAME_VERSION], "loaders": [target], "changelog": manifest["changelog"],
            "featured": False,
            "dependencies": [{"project_id": TARGETS[target]["dependency"], "dependency_type": "required"}],
        }
        if metadata != expected:
            raise ValueError(f"Unexpected release metadata: {target}")
        jar = path.parent / record["filename"]
        if digest(jar) != record["sha512"] or jar.stat().st_size != record["size"]:
            raise ValueError(f"Artifact checksum mismatch: {target}")
    expected_sums = "".join(f"{v['sha512']}  {v['filename']}\n" for v in records)
    if (path.parent / "SHA512SUMS").read_text(encoding="utf-8") != expected_sums:
        raise ValueError("SHA512SUMS does not match the manifest")
    return manifest


class Modrinth:
    def __init__(self, token):
        if not token:
            raise ValueError("MODRINTH_TOKEN is required")
        self.token = token

    def request(self, path, data=None, content_type=None):
        headers = {"Authorization": self.token, "User-Agent": f"{REPOSITORY}/release"}
        if content_type:
            headers["Content-Type"] = content_type
        request = urllib.request.Request("https://api.modrinth.com/v2/" + path, data=data, headers=headers)
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.load(response)

    def versions(self):
        return self.request(f"project/{PROJECT_ID}/version")

    def get(self, version_id):
        return self.request(f"version/{version_id}")

    def create(self, record, directory):
        boundary = "lvb-" + uuid.uuid4().hex
        metadata = dict(record["metadata"], file_parts=["file"], primary_file="file")
        data = (
            f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
            'Content-Type: application/json\r\n\r\n' + json.dumps(metadata) + '\r\n'
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{record["filename"]}"\r\n'
            'Content-Type: application/java-archive\r\n\r\n'
        ).encode("utf-8")
        data += (directory / record["filename"]).read_bytes() + f"\r\n--{boundary}--\r\n".encode("ascii")
        return self.request("version", data, f"multipart/form-data; boundary={boundary}")


def check_existing(record, existing):
    metadata = record["metadata"]
    for key in ("project_id", "name", "version_number", "version_type", "game_versions", "loaders", "changelog"):
        if existing.get(key) != metadata[key]:
            raise ValueError(f"Published metadata differs for {metadata['version_number']}: {key}")
    dependencies = [(d.get("project_id"), d.get("version_id"), d.get("file_name"), d["dependency_type"])
                    for d in existing.get("dependencies", [])]
    expected = [(metadata["dependencies"][0]["project_id"], None, None, "required")]
    files = existing.get("files", [])
    if dependencies != expected or len(files) != 1:
        raise ValueError(f"Published files or dependencies differ for {metadata['version_number']}")
    file = files[0]
    if (file["filename"] != record["filename"] or file["hashes"]["sha512"] != record["sha512"]
            or file["size"] != record["size"] or not file["primary"]):
        raise ValueError(f"Published artifact differs for {metadata['version_number']}")
    return existing["id"]


def check_github_release(manifest, release):
    if (release["tag_name"] != manifest["tag"] or release["name"] != manifest["version"]
            or release["body"] != manifest["changelog"]
            or release["prerelease"] != (release_channel(manifest["version"]) != "release")):
        raise ValueError("Existing GitHub release metadata differs")


def preflight(records, versions):
    completed = {}
    for record in records:
        matches = [v for v in versions if v["version_number"] == record["metadata"]["version_number"]]
        if len(matches) > 1:
            raise ValueError(f"Duplicate published version: {record['metadata']['version_number']}")
        if matches:
            completed[record["target"]] = check_existing(record, matches[0])
    return completed


def publish_modrinth(client, records, directory, completed):
    result = dict(completed)
    for record in records:
        target = record["target"]
        if target in result:
            continue
        try:
            created = client.create(record, directory)
        except (OSError, urllib.error.URLError):
            # A timeout can occur after the server commits the upload. Reconcile before retrying.
            reconciled = preflight(records, client.versions())
            if target not in reconciled:
                raise
            result.update(reconciled)
        else:
            result[target] = check_existing(record, client.get(created["id"]))
    return result


def gh(*args, allow_missing=False):
    result = subprocess.run(["gh", *args], text=True, capture_output=True, check=False)
    if result.returncode:
        if allow_missing:
            try:
                if str(json.loads(result.stdout).get("status")) == "404":
                    return None
            except (ValueError, AttributeError):
                pass
        raise RuntimeError(f"GitHub CLI operation failed: {result.stderr.strip()}")
    return result.stdout


def publish(manifest_path):
    manifest = load_manifest(manifest_path)
    directory, tag = manifest_path.parent, manifest["tag"]
    client = Modrinth(os.environ.get("MODRINTH_TOKEN"))
    completed = preflight(manifest["versions"], client.versions())
    commit = json.loads(gh("api", f"repos/{REPOSITORY}/commits/{tag}"))["sha"]
    if commit != manifest["commit"]:
        raise ValueError("Release tag no longer matches the built commit")
    raw = gh("api", f"repos/{REPOSITORY}/releases/tags/{tag}", allow_missing=True)
    release = json.loads(raw) if raw else None
    assets = [v["filename"] for v in manifest["versions"]] + ["release.json", "SHA512SUMS"]
    present = set()
    with tempfile.TemporaryDirectory(prefix="lvb-release-") as tmp:
        notes = Path(tmp) / "notes.md"
        notes.write_text(manifest["changelog"], encoding="utf-8")
        if release:
            check_github_release(manifest, release)
            for asset in release["assets"]:
                if asset["name"] not in assets:
                    raise ValueError("Unexpected existing GitHub release asset")
                gh("release", "download", tag, "--repo", REPOSITORY, "--pattern", asset["name"], "--dir", tmp)
                if digest(Path(tmp) / asset["name"]) != digest(directory / asset["name"]):
                    raise ValueError(f"Existing GitHub release asset differs: {asset['name']}")
                present.add(asset["name"])
            if not release["draft"] and present != set(assets):
                raise ValueError("Published GitHub release is incomplete")
        else:
            args = ["release", "create", tag, "--repo", REPOSITORY, "--verify-tag", "--draft",
                    "--title", manifest["version"], "--notes-file", str(notes)]
            if release_channel(manifest["version"]) != "release":
                args.append("--prerelease")
            gh(*args)
        for asset in assets:
            if asset not in present:
                gh("release", "upload", tag, str(directory / asset), "--repo", REPOSITORY)
        published = publish_modrinth(client, manifest["versions"], directory, completed)
        preflight(manifest["versions"], [client.get(version_id) for version_id in published.values()])
        if not release or release["draft"]:
            gh("release", "edit", tag, "--repo", REPOSITORY, "--draft=false")
    print(json.dumps({"tag": tag, "modrinth_versions": published}, sort_keys=True))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    staging = commands.add_parser("stage")
    staging.add_argument("--base-version", required=True)
    staging.add_argument("--report", type=Path, required=True)
    staging.add_argument("--output", type=Path, required=True)
    staging.add_argument("--tag")
    for target in TARGETS:
        staging.add_argument(f"--{target}", type=Path, required=True)
    publishing = commands.add_parser("publish")
    publishing.add_argument("manifest", type=Path)
    checking = commands.add_parser("validate-tag")
    checking.add_argument("tag")
    checking.add_argument("base")
    args = parser.parse_args()
    if args.command == "publish":
        publish(args.manifest)
    elif args.command == "validate-tag":
        validate_tag(args.tag, args.base)
    else:
        stage(args.base_version, args.report,
              {target: getattr(args, target.replace("-", "_")) for target in TARGETS}, args.output, args.tag)


if __name__ == "__main__":
    main()
