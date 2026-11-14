import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import test_mod_compatibility as runner


class CompatibilityTests(unittest.TestCase):
    def setUp(self):
        output = patch("builtins.print")
        output.start()
        self.addCleanup(output.stop)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.profile = self.root / "profile"
        self.mod = self.root / "fixture.jar"
        with zipfile.ZipFile(self.mod, "w") as archive:
            archive.writestr("fabric.mod.json", '{"id":"fixture","version":"1.0.0"}')
        self.minecraft = self.root / "minecraft.jar"
        self.minecraft.write_bytes(b"minecraft fixture")
        self.manifest = self.root / "launch.json"
        self.manifest.write_text(json.dumps({
            "platform": "legacy-fabric", "java": "java-fixture", "mods": [str(self.mod)],
            "classpath": [], "natives": [], "core": "core.jar", "minecraft": str(self.minecraft),
            "assets": "assets", "assetIndex": "1.8", "version": "1.3.0+legacy-fabric",
            "mapping": {"generation": 1, "namespace": "legacy-intermediary", "build": "568"},
        }), encoding="utf-8")
        self.client = {"schema": 1, "platform": "legacy-fabric", "runtime": {
            "java.version": "1.8.0", "lwjgl": "2.9.4", "gl.version": "2.1", "vao": "unavailable",
        }, "assertions": sorted(runner.BASE_ASSERTIONS)}

    def client_process(self, command, **kwargs):
        (self.profile / "smoke.txt").write_text("PASS: fixture", encoding="utf-8")
        (self.profile / "client.json").write_text(json.dumps(self.client), encoding="utf-8")
        return subprocess.CompletedProcess(command, 0)

    def launch(self, **kwargs):
        with patch.object(runner.subprocess, "run", side_effect=self.client_process):
            code = runner.launch(self.manifest, self.profile, [], **kwargs)
        return code, json.loads((self.profile / "result.json").read_text(encoding="utf-8"))

    def test_records_installed_artifacts_and_settings(self):
        config = self.root / "settings"
        config.mkdir()
        (config / "overlay.json").write_text('{"enabled":true}', encoding="utf-8")
        options = self.root / "optionsof.txt"
        options.write_text("ofFastRender:true\n", encoding="utf-8")
        code, result = self.launch(config=config, options=[options])
        self.assertEqual(0, code)
        self.assertEqual(runner.digest(self.mod), result["mods"][0]["sha256"])
        self.assertEqual("fixture", result["mods"][0]["id"])
        self.assertEqual(runner.digest(config / "overlay.json"), result["configuration_before"]["config/overlay.json"])
        self.assertEqual(self.client, result["client"])
        self.assertEqual(runner.digest(options), result["configuration_before"]["optionsof.txt"])
        self.assertEqual("", (self.profile / "options.txt").read_text())


    def test_enabled_feature_requires_executed_assertion(self):
        code, result = self.launch(properties=["lvb.smoke.expectHitboxes=true"])
        self.assertEqual(1, code)
        self.assertIn("feature:expectHitboxes", result["failure"])

    def test_shader_pack_requires_an_active_shader_program(self):
        shader = self.root / "shader.zip"
        with zipfile.ZipFile(shader, "w") as archive:
            archive.writestr("shaders/gbuffers_water.vsh", "shader fixture")
        code, result = self.launch(shader_pack=shader)
        self.assertEqual(1, code)
        self.assertIn("shader-pack-active", result["failure"])
        self.profile = self.root / "active-shader"
        self.client["assertions"].append("shader-pack-active")
        code, result = self.launch(shader_pack=shader)
        self.assertEqual(0, code)
        self.assertEqual(runner.digest(shader), result["shader_pack"]["sha256"])
        self.assertEqual(shader.read_bytes(), (self.profile / "shaderpacks/shader.zip").read_bytes())
        self.assertIn("shaderPack=shader.zip", (self.profile / "optionsshaders.txt").read_text())


    def test_crashed_client_cannot_pass_with_a_report(self):
        process = self.client_process
        def crash(command, **kwargs):
            process(command, **kwargs)
            return subprocess.CompletedProcess(command, 1)
        self.client_process = crash
        code, result = self.launch()
        self.assertEqual(1, code)
        self.assertFalse(result["passed"])

    def test_timeout_is_recorded(self):
        with patch.object(runner.subprocess, "run", side_effect=subprocess.TimeoutExpired("fixture", 240)):
            self.assertEqual(1, runner.launch(self.manifest, self.profile, []))
        self.assertIn("240 seconds", json.loads((self.profile / "result.json").read_text())["failure"])

    def test_reused_profile_is_rejected_before_launch(self):
        self.profile.mkdir()
        with patch.object(runner.subprocess, "run") as process, self.assertRaises(FileExistsError):
            runner.launch(self.manifest, self.profile, [])
        process.assert_not_called()

    def test_misspelled_and_conflicting_features_are_rejected_before_profile_creation(self):
        for properties in [["lvb.smoke.hitbox=true"], ["lvb.smoke.villages=true"],
                           ["lvb.smoke.expectHitboxes=true", "lvb.smoke.expectHitboxes=false"]]:
            with self.subTest(properties=properties), self.assertRaises(ValueError):
                runner.launch(self.manifest, self.profile, [], properties=properties)
            self.assertFalse(self.profile.exists())


if __name__ == "__main__":
    unittest.main()
