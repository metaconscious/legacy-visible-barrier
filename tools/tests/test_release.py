import hashlib
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release import check_github_release, load_manifest, publish, stage
from versions import TARGETS, artifact_version


def record(target, base="1.3.0"):
    data = b"verified-test-artifact"
    version = artifact_version(base, target)
    return {"target": target, "version": version, "filename": f"legacy-visible-barrier-{version}.jar",
            "sha512": hashlib.sha512(data).hexdigest(), "size": len(data)}


def write_manifest(directory, records, base="1.3.0"):
    manifest = {"schema": 2, "repository": "metaconscious/legacy-visible-barrier",
                "version": base, "tag": f"v{base}", "commit": "a" * 40,
                "changelog": "- Unified build.", "artifacts": records}
    path = directory / "release.json"
    path.write_text(json.dumps(manifest), encoding="utf-8")
    for artifact in records:
        (directory / artifact["filename"]).write_bytes(b"verified-test-artifact")
    (directory / "SHA512SUMS").write_text("".join(
        f"{v['sha512']}  {v['filename']}\n" for v in records), encoding="utf-8")
    return path


class FakeGitHub:
    def __init__(self, manifest, existing=None, files=None):
        self.manifest = manifest
        self.existing = existing
        self.extra_releases = []
        self.files = dict(files or {})
        self.created = 0
        self.uploaded = []
        self.published = 0

    def __call__(self, *args):
        if args[0] == "api":
            endpoint = args[1]
            if "/commits/" in endpoint:
                return json.dumps({"sha": self.manifest["commit"]})
            if "/releases/tags/" in endpoint:
                # GitHub's tag endpoint only exposes published releases.
                return json.dumps(self.existing) if self.existing and not self.existing["draft"] else None
            if endpoint.split("?")[0].endswith("/releases"):
                pages = [[{"tag_name": f"v0.0.{n}"} for n in range(100)],
                         ([self.existing] if self.existing else []) + self.extra_releases]
                if "--paginate" not in args:
                    return json.dumps(pages[0])
                if "--slurp" in args:
                    return json.dumps(pages)
                return "\n".join(json.dumps(page) for page in pages)
        if args[:2] == ("release", "create"):
            if self.existing:
                raise AssertionError("An existing draft must be resumed without creating another release")
            self.created += 1
            self.existing = {"tag_name": self.manifest["tag"], "name": self.manifest["version"],
                             "body": self.manifest["changelog"], "prerelease": "--prerelease" in args,
                             "draft": True, "assets": []}
        elif args[:2] == ("release", "download"):
            name = args[args.index("--pattern") + 1]
            directory = Path(args[args.index("--dir") + 1])
            (directory / name).write_bytes(self.files[name])
        elif args[:2] == ("release", "upload"):
            path = Path(args[3])
            if path.name in self.files:
                raise AssertionError("Existing release assets must not be uploaded again")
            self.files[path.name] = path.read_bytes()
            self.existing["assets"].append({"name": path.name})
            self.uploaded.append(path.name)
        elif args[:2] == ("release", "edit"):
            self.existing["draft"] = False
            self.published += 1
        else:
            raise AssertionError(f"Unexpected GitHub operation: {args}")
        return ""


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.records = [record(target) for target in TARGETS]

    def test_github_release_metadata_must_match_on_retry(self):
        manifest = {"version": "1.3.0", "tag": "v1.3.0", "changelog": "- Unified build."}
        existing = {"tag_name": "v1.3.0", "name": "1.3.0", "body": "- Unified build.", "prerelease": False}
        check_github_release(manifest, existing)
        for field, value in (("tag_name", "v1.1.0"), ("name", "another release"),
                             ("body", "changed notes"), ("prerelease", True)):
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "metadata differs"):
                check_github_release(manifest, dict(existing, **{field: value}))

    def test_staged_manifest_preserves_all_targets_and_checks_actual_artifact_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            write_manifest(path, self.records)
            report = path / "artifacts.json"
            report.write_text(json.dumps(self.records), encoding="utf-8")
            (path / "CHANGELOG.md").write_text("## [1.3.0]\n\n- Unified build.\n", encoding="utf-8")
            output = path / "staged"
            with (patch("release.ROOT", path),
                  patch("release.subprocess.check_output", side_effect=["", "a" * 40]),
                  redirect_stdout(io.StringIO())):
                stage("1.3.0", report, {r["target"]: path / r["filename"] for r in self.records}, output)
            manifest_path = output / "release.json"
            self.assertEqual(self.records, load_manifest(manifest_path)["artifacts"])
            (output / self.records[0]["filename"]).write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                load_manifest(manifest_path)

    def test_retry_resumes_a_draft_on_a_later_page_and_uploads_only_missing_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            manifest_path = write_manifest(path, self.records)
            manifest = load_manifest(manifest_path)
            first_file = self.records[0]["filename"]
            draft = {"tag_name": "v1.3.0", "name": "1.3.0", "body": manifest["changelog"],
                     "prerelease": False, "draft": True, "assets": [{"name": first_file}]}
            github = FakeGitHub(manifest, draft, {first_file: (path / first_file).read_bytes()})
            with patch("release.gh", github), redirect_stdout(io.StringIO()):
                publish(manifest_path)
            self.assertEqual(0, github.created)
            self.assertEqual([r["filename"] for r in self.records[1:]] + ["release.json", "SHA512SUMS"], github.uploaded)
            self.assertEqual(1, github.published)
            self.assertFalse(github.existing["draft"])
            for name, data in github.files.items():
                self.assertEqual((path / name).read_bytes(), data)

    def test_first_publication_and_published_release_retry_create_one_release(self):
        for base in ("1.3.0", "1.3.0-rc.1"):
            with self.subTest(version=base), tempfile.TemporaryDirectory() as directory:
                records = [record(target, base) for target in TARGETS]
                manifest_path = write_manifest(Path(directory), records, base)
                github = FakeGitHub(load_manifest(manifest_path))
                with patch("release.gh", github), redirect_stdout(io.StringIO()):
                    publish(manifest_path)
                    publish(manifest_path)
                self.assertEqual(1, github.created)
                self.assertEqual(1, github.published)
                self.assertEqual([r["filename"] for r in records] + ["release.json", "SHA512SUMS"], github.uploaded)
                self.assertEqual(base != "1.3.0", github.existing["prerelease"])

    def test_duplicate_github_tags_fail_before_any_publication(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest_path = write_manifest(Path(directory), self.records)
            github = FakeGitHub(load_manifest(manifest_path), {"tag_name": "v1.3.0", "draft": True})
            github.extra_releases.append({"tag_name": "v1.3.0", "draft": False})
            with patch("release.gh", github), self.assertRaisesRegex(ValueError, "Multiple GitHub releases"):
                publish(manifest_path)
            self.assertEqual(0, github.created)
            self.assertEqual([], github.uploaded)
            self.assertEqual(0, github.published)


if __name__ == "__main__":
    unittest.main()
