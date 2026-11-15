import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from versions import artifact_version, changelog_entry, parse_version, validate_tag


class VersionTests(unittest.TestCase):
    def test_valid_semver_identifiers(self):
        for value in ("0.0.0", "1.2.0", "1.2.0+legacy-fabric", "1.2.0+ornithe", "1.2.0-rc.1+ornithe",
                      "1.0.0-alpha.0", "1.0.0+001", "1.0.0-x-y-z.--"):
            with self.subTest(value=value):
                parse_version(value)

    def test_invalid_versions_are_rejected(self):
        for value in ("v1.2.0", "01.2.0", "1.02.0", "1.2.00", "1.2", "1.2.0.0", "1.2.0-01",
                      "1.2.0-rc.01", "1.2.0+", "1.2.0-alpha..1", "1.2.0+foo_bar", "1.2.0\n",
                      "١.2.0", "mc1.8.9-1.2.0-ornithe", "1.2.0+one+two"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                parse_version(value)

    def test_platform_variants_are_derived_from_one_version(self):
        self.assertEqual("1.2.0+legacy-fabric", artifact_version("1.2.0", "legacy-fabric"))
        self.assertEqual("1.2.0-rc.1+ornithe", artifact_version("1.2.0-rc.1", "ornithe"))
        self.assertEqual("1.3.0+ornithe.gen2", artifact_version("1.3.0", "ornithe.gen2"))
        for base, target in (("1.2.0+custom", "ornithe"), ("1.2.0", "fabric")):
            with self.assertRaises(ValueError):
                artifact_version(base, target)

    def test_tag_must_exactly_match_release_version(self):
        validate_tag("v1.2.0", "1.2.0")
        for tag in ("1.2.0", "v1.2.1", "v1.2.0+ornithe", "v1.2.0\n"):
            with self.assertRaises(ValueError):
                validate_tag(tag, "1.2.0")

    def test_changelog_selects_only_the_requested_release(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "CHANGELOG.md"
            path.write_text("# Changelog\n\n## [1.2.0] - Unreleased\n\n- Unified build.\n\n"
                            "## [1.1.0]\n\n- Ornithe port.\n", encoding="utf-8")
            self.assertEqual("- Unified build.", changelog_entry(path, "1.2.0"))
            with self.assertRaises(ValueError):
                changelog_entry(path, "1.3.0")


if __name__ == "__main__":
    unittest.main()
