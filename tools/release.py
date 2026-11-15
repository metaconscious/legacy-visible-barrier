"""Stage verified distributions, or publish them through GitHub CLI."""

import argparse
import hashlib
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

from versions import TARGETS, artifact_version, changelog_entry, parse_version, validate_tag

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
        raise ValueError("All distributions must pass artifact verification")
    output.mkdir(parents=True, exist_ok=True)
    artifacts = []
    for target in TARGETS:
        jar, report = jars[target], reports[target]
        version = artifact_version(base, target)
        if report["version"] != version or jar.name != report["filename"] or digest(jar) != report["sha512"]:
            raise ValueError(f"Artifact changed after verification: {target}")
        shutil.copyfile(jar, output / jar.name)
        artifacts.append({
            "target": target, "version": version, "filename": jar.name,
            "sha512": report["sha512"], "size": report["size"],
        })
    manifest = {"schema": 2, "repository": REPOSITORY, "version": base, "tag": f"v{base}",
                "commit": commit, "changelog": changelog, "artifacts": artifacts}
    (output / "release.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output / "SHA512SUMS").write_text(
        "".join(f"{v['sha512']}  {v['filename']}\n" for v in artifacts), encoding="utf-8")
    print(f"Staged {base} from {commit} in {output}")


def load_manifest(path):
    manifest = json.loads(path.read_text(encoding="utf-8"))
    validate_tag(manifest["tag"], manifest["version"])
    if manifest["schema"] != 2 or manifest["repository"] != REPOSITORY:
        raise ValueError("Unexpected release manifest")
    records = manifest["artifacts"]
    if len(records) != len(TARGETS) or {v["target"] for v in records} != set(TARGETS):
        raise ValueError("A release must contain exactly one artifact per target")
    for record in records:
        target = record["target"]
        version = artifact_version(manifest["version"], target)
        if record["version"] != version or record["filename"] != f"legacy-visible-barrier-{version}.jar":
            raise ValueError(f"Unexpected distribution version or filename: {target}")
        jar = path.parent / record["filename"]
        if digest(jar) != record["sha512"] or jar.stat().st_size != record["size"]:
            raise ValueError(f"Artifact checksum mismatch: {target}")
    expected_sums = "".join(f"{v['sha512']}  {v['filename']}\n" for v in records)
    if (path.parent / "SHA512SUMS").read_text(encoding="utf-8") != expected_sums:
        raise ValueError("SHA512SUMS does not match the manifest")
    return manifest


def check_github_release(manifest, release):
    if (release["tag_name"] != manifest["tag"] or release["name"] != manifest["version"]
            or release["body"] != manifest["changelog"]
            or release["prerelease"] != bool(parse_version(manifest["version"])["prerelease"])):
        raise ValueError("Existing GitHub release metadata differs")


def gh(*args):
    result = subprocess.run(["gh", *args], text=True, capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError(f"GitHub CLI operation failed: {result.stderr.strip()}")
    return result.stdout


def find_github_release(tag):
    pages = json.loads(gh("api", f"repos/{REPOSITORY}/releases?per_page=100", "--paginate", "--slurp"))
    matches = [release for page in pages for release in page if release["tag_name"] == tag]
    if len(matches) > 1:
        raise ValueError(f"Multiple GitHub releases use tag {tag}")
    return matches[0] if matches else None


def publish(manifest_path):
    manifest = load_manifest(manifest_path)
    directory, tag = manifest_path.parent, manifest["tag"]
    commit = json.loads(gh("api", f"repos/{REPOSITORY}/commits/{tag}"))["sha"]
    if commit != manifest["commit"]:
        raise ValueError("Release tag no longer matches the built commit")
    release = find_github_release(tag)
    assets = [v["filename"] for v in manifest["artifacts"]] + ["release.json", "SHA512SUMS"]
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
            if parse_version(manifest["version"])["prerelease"]:
                args.append("--prerelease")
            gh(*args)
        for asset in assets:
            if asset not in present:
                gh("release", "upload", tag, str(directory / asset), "--repo", REPOSITORY)
        if not release or release["draft"]:
            gh("release", "edit", tag, "--repo", REPOSITORY, "--draft=false")
    print(f"Published GitHub release {tag}")


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
