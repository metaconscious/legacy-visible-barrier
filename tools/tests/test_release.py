import copy
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release import check_existing, check_github_release, load_manifest, preflight, publish_modrinth
from versions import PROJECT_ID, TARGETS


def record(target):
    data = b"verified-test-artifact"
    return {"target": target, "filename": f"legacy-visible-barrier-1.2.0+{target}.jar",
            "sha512": hashlib.sha512(data).hexdigest(), "size": len(data),
            "metadata": {"project_id": PROJECT_ID, "name": f"1.2.0 — {TARGETS[target]['name']}",
                         "version_number": f"1.2.0+{target}", "version_type": "release",
                         "game_versions": ["1.8.9"], "loaders": [target], "featured": False,
                         "changelog": "- Unified build.",
                         "dependencies": [{"project_id": TARGETS[target]["dependency"],
                                           "dependency_type": "required"}]}}


def published(artifact):
    result = copy.deepcopy(artifact["metadata"])
    result.update(id=f"id-{artifact['target']}", files=[{
        "filename": artifact["filename"], "size": artifact["size"], "primary": True,
        "hashes": {"sha512": artifact["sha512"]},
    }])
    return result


class FakeModrinth:
    def __init__(self, versions=(), timeout_after_upload=False):
        self.existing = list(versions)
        self.created = []
        self.timeout_after_upload = timeout_after_upload

    def versions(self):
        return self.existing

    def create(self, artifact, directory):
        self.created.append(artifact["target"])
        result = published(artifact)
        self.existing.append(result)
        if self.timeout_after_upload:
            raise TimeoutError("Response lost after successful upload")
        return result

    def get(self, version_id):
        return next(v for v in self.existing if v["id"] == version_id)


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.records = [record(target) for target in TARGETS]

    def test_new_release_has_two_missing_targets(self):
        self.assertEqual({}, preflight(self.records, []))

    def test_matching_existing_artifact_is_complete(self):
        self.assertEqual({"legacy-fabric": "id-legacy-fabric"},
                         preflight(self.records, [published(self.records[0])]))

    def test_both_variants_publish_as_separate_records(self):
        client = FakeModrinth()
        result = publish_modrinth(client, self.records, Path("."), preflight(self.records, client.versions()))
        self.assertEqual({"legacy-fabric", "ornithe"}, set(result))
        self.assertEqual(["legacy-fabric", "ornithe"], client.created)

    def test_partial_release_retries_only_the_missing_target(self):
        client = FakeModrinth([published(self.records[0])])
        publish_modrinth(client, self.records, Path("."), preflight(self.records, client.versions()))
        self.assertEqual(["ornithe"], client.created)

    def test_complete_release_retry_uploads_nothing(self):
        client = FakeModrinth([published(v) for v in self.records])
        publish_modrinth(client, self.records, Path("."), preflight(self.records, client.versions()))
        self.assertEqual([], client.created)

    def test_duplicate_version_numbers_are_rejected(self):
        existing = published(self.records[0])
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            preflight(self.records, [existing, existing])

    def test_existing_version_with_another_loader_is_rejected(self):
        existing = published(self.records[0])
        existing["loaders"] = ["ornithe"]
        with self.assertRaisesRegex(ValueError, "loaders"):
            preflight(self.records, [existing])

    def test_existing_file_cannot_be_replaced(self):
        existing = published(self.records[0])
        existing["files"][0]["hashes"]["sha512"] = "0" * 128
        with self.assertRaisesRegex(ValueError, "artifact differs"):
            preflight(self.records, [existing])

    def test_changed_dependency_cannot_be_published_under_same_version(self):
        existing = published(self.records[0])
        existing["dependencies"][0]["project_id"] = "wrong-project"
        with self.assertRaisesRegex(ValueError, "dependencies differ"):
            check_existing(self.records[0], existing)

    def test_changed_channel_cannot_be_published_under_same_version(self):
        existing = published(self.records[0])
        existing["version_type"] = "beta"
        with self.assertRaisesRegex(ValueError, "version_type"):
            check_existing(self.records[0], existing)

    def test_additional_dependency_cannot_be_published_under_same_version(self):
        existing = published(self.records[0])
        existing["dependencies"].append({"version_id": "extra-version", "dependency_type": "required"})
        with self.assertRaisesRegex(ValueError, "dependencies differ"):
            check_existing(self.records[0], existing)

    def test_github_release_metadata_must_match_on_retry(self):
        manifest = {"version": "1.2.0", "tag": "v1.2.0", "changelog": "- Unified build."}
        existing = {"tag_name": "v1.2.0", "name": "1.2.0", "body": "- Unified build.", "prerelease": False}
        check_github_release(manifest, existing)
        for field, value in (("tag_name", "v1.1.0"), ("name", "another release"),
                             ("body", "changed notes"), ("prerelease", True)):
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "metadata differs"):
                check_github_release(manifest, dict(existing, **{field: value}))

    def test_both_records_are_checked_before_uploading_either(self):
        existing = published(self.records[1])
        existing["game_versions"] = ["1.12.2"]
        client = FakeModrinth([existing])
        with self.assertRaises(ValueError):
            completed = preflight(self.records, client.versions())
            publish_modrinth(client, self.records, Path("."), completed)
        self.assertEqual([], client.created)

    def test_uncertain_response_is_reconciled_without_duplicate_upload(self):
        client = FakeModrinth(timeout_after_upload=True)
        result = publish_modrinth(client, self.records, Path("."), {})
        self.assertEqual({"legacy-fabric", "ornithe"}, set(result))
        self.assertEqual(["legacy-fabric", "ornithe"], client.created)

    def test_manifest_checks_the_actual_artifact_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            manifest = {"schema": 1, "repository": "metaconscious/legacy-visible-barrier",
                        "version": "1.2.0", "tag": "v1.2.0", "commit": "a" * 40,
                        "changelog": "- Unified build.", "versions": self.records}
            manifest_path = path / "release.json"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            for artifact in self.records:
                (path / artifact["filename"]).write_bytes(b"verified-test-artifact")
            (path / "SHA512SUMS").write_text("".join(
                f"{v['sha512']}  {v['filename']}\n" for v in self.records), encoding="utf-8")
            self.assertEqual("1.2.0", load_manifest(manifest_path)["version"])
            (path / self.records[0]["filename"]).write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                load_manifest(manifest_path)


if __name__ == "__main__":
    unittest.main()
