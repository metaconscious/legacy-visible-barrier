import json
import struct
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path, PureWindowsPath

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_artifacts import verify_jar

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared-resources/src/main/resources/assets"


class WindowsAsset:
    def __init__(self, path):
        self.path = path

    def relative_to(self, root):
        return PureWindowsPath(self.path.relative_to(root.path))

    def is_file(self):
        return self.path.is_file()

    def read_bytes(self):
        return self.path.read_bytes()


class WindowsAssets:
    """Expose Windows path semantics over real fixture files on any test host."""
    def __init__(self, path):
        self.path = path

    def rglob(self, pattern):
        return (WindowsAsset(path) for path in self.path.rglob(pattern))


def empty_class(name):
    """A Java 8 class extending Object, with no members or attributes."""
    name = name.encode("ascii")
    constants = (b"\x01" + struct.pack(">H", len(name)) + name + b"\x07\x00\x01"
                 + b"\x01\x00\x10java/lang/Object\x07\x00\x03")
    return struct.pack(">IHHH", 0xCAFEBABE, 0, 52, 5) + constants + struct.pack(">7H", 0x21, 2, 4, 0, 0, 0, 0)


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.jar = Path(self.directory.name) / "legacy-visible-barrier-1.2.0+legacy-fabric.jar"
        resources = ROOT / "legacy-fabric/src/main/resources"
        metadata = (resources / "fabric.mod.json").read_text(encoding="utf-8")
        metadata = metadata.replace("${version}", "1.2.0+legacy-fabric").replace("${minecraftVersion}", "1.8.9")
        config = json.loads((resources / "lvb-legacy-fabric.mixins.json").read_text(encoding="utf-8"))
        core_name = "io/github/metaconscious/lvb/core/VisibilityController"
        self.core = {core_name + ".class": empty_class(core_name)}
        self.entries = dict(self.core)
        self.entries.update({
            "LICENSE": b"CC0-1.0",
            "fabric.mod.json": metadata.encode("utf-8"),
            "lvb-legacy-fabric.mixins.json": json.dumps(config).encode("utf-8"),
            config["refmap"]: b'{"mappings":{"fixture":{}}}',
        })
        entrypoint = json.loads(metadata)["entrypoints"]["client"][0].replace(".", "/")
        for name in [entrypoint, config["plugin"].replace(".", "/")] + [config["package"].replace(".", "/") + "/" + mixin for mixin in config["client"]]:
            self.entries[name + ".class"] = empty_class(name)
        for path in ASSETS.rglob("*"):
            if path.is_file():
                self.entries["assets/" + path.relative_to(ASSETS).as_posix()] = path.read_bytes()

    def write_jar(self):
        with zipfile.ZipFile(self.jar, "w") as jar:
            for name, data in self.entries.items():
                jar.writestr(name, data)

    def test_windows_asset_paths_match_portable_jar_entries(self):
        self.write_jar()
        for assets in (ASSETS, WindowsAssets(ASSETS)):
            with self.subTest(filesystem=type(assets).__name__):
                report = verify_jar(self.jar, "legacy-fabric", "1.2.0", self.core, assets)
                self.assertEqual(13, report["asset_count"])

    def test_windows_paths_still_require_identical_resource_bytes(self):
        self.entries["assets/lvb/icon.png"] = b"changed asset"
        self.write_jar()
        with self.assertRaisesRegex(ValueError, "Shared resource changed during packaging: assets/lvb/icon.png"):
            verify_jar(self.jar, "legacy-fabric", "1.2.0", self.core, WindowsAssets(ASSETS))

    def test_barrier_frame_hook_must_be_packaged(self):
        del self.entries["io/github/metaconscious/lvb/legacyfabric/mixin/GameRendererMixin.class"]
        self.write_jar()
        with self.assertRaisesRegex(ValueError, "Missing Mixin GameRendererMixin"):
            verify_jar(self.jar, "legacy-fabric", "1.2.0", self.core, ASSETS)

    def test_smoke_mixin_configuration_must_not_be_packaged(self):
        self.entries["lvb-legacy-fabric-smoke.mixins.json"] = b"{}"
        self.write_jar()
        with self.assertRaisesRegex(ValueError, "Test mod files leaked"):
            verify_jar(self.jar, "legacy-fabric", "1.2.0", self.core, ASSETS)


if __name__ == "__main__":
    unittest.main()
