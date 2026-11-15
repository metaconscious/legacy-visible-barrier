"""Run remapped client smoke tests with supplied mod JARs in a fresh local profile."""

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile
from pathlib import Path

from versions import TARGETS

FEATURES = {
    "legacy-fabric": {"chunkBorders", "expectHitboxes", "expectHighlights", "orthographic", "axolotlOutlines"},
    "ornithe": {"villages", "waypoints", "axolotlOutlines", "miningProgress"},
}
BASE_ASSERTIONS = {
    "startup-controls", "mode:enhanced", "runtime-native-handover", "shared-failure-unavailable",
    "barrier-pixels-vbo-on-off", "zero-toggle-terrain-invalidations",
    "batch-failure-recovery", "index-thread-ownership", "foreign-gl-state-and-failure",
    "camera-pass-counts", "empty-camera-pass-counts", "block-chunk-lifecycle",
    "resource-reload", "world-switch", "translucent-neighbor-pixels", "shared-terrain-submission",
}
NATIVE_ASSERTIONS = {
    "startup-controls", "mode:native", "native-startup-isolation", "native-toggles-vbo-on-off",
    "resource-reload", "world-switch", "native-translucent-scene",
}
OFF_ASSERTIONS = {"startup-controls", "mode:off", "native-startup-isolation", "off-controls"}
OPTION_FILES = {"options.txt", "optionsof.txt", "optionsshaders.txt"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def configurations(profile):
    files = list((profile / "config").rglob("*")) + [profile / name for name in OPTION_FILES]
    return {p.relative_to(profile).as_posix(): digest(p) for p in sorted(files) if p.is_file()}


def validate_properties(platform, properties):
    allowed = {f"lvb.smoke.{feature}={value}" for feature in FEATURES[platform] for value in ("true", "false")}
    keys = [p.partition("=")[0] for p in properties]
    if any(p not in allowed for p in properties) or len(set(keys)) != len(keys):
        raise ValueError(f"Unsupported or duplicate feature assertion for {platform}")


def validate_client(result):
    client = result.get("client", {})
    if client.get("schema") != 1 or client.get("platform") != result["platform"]:
        return "Missing or mismatched client report"
    runtime = client.get("runtime", {})
    if not all(runtime.get(key) for key in ("java.version", "lwjgl", "gl.version")):
        return "Incomplete runtime identity"
    mode = result.get("render_mode", "enhanced")
    required = (OFF_ASSERTIONS if mode == "off" else NATIVE_ASSERTIONS if mode == "native" else BASE_ASSERTIONS)
    if result.get("missing_hook"):
        required = NATIVE_ASSERTIONS - {"native-startup-isolation"} | {"missing-hook-recovery"}
    required = required | {
        "feature:" + p.split(".")[-1].split("=")[0] for p in result["properties"] if p.endswith("=true")
    }
    if mode == "enhanced" and not result.get("missing_hook") and runtime.get("vao") in ("core", "arb"):
        required.add("foreign-vao")
    if result.get("shader_pack"):
        required.add("shader-pack-active")
    missing = required - set(client.get("assertions", []))
    return "Missing assertions: " + ", ".join(sorted(missing)) if missing else None


def is_mod(jar):
    with zipfile.ZipFile(jar) as archive:
        return "fabric.mod.json" in archive.namelist()


def launch(manifest, profile, mods, java=None, properties=(), config=None, options=(), shader_pack=None,
           render_mode="enhanced", native_refresh="dirty", missing_hook=False, runtime_manifest=None, api=None):
    data = json.loads(manifest.read_text(encoding="utf-8"))
    if runtime_manifest:
        runtime = json.loads(runtime_manifest.read_text(encoding="utf-8"))
        if runtime["mapping"] == data["mapping"]:
            raise ValueError("The negative launch needs a different mapping environment")
        for key in ("classpath", "minecraft", "natives", "assets", "assetIndex", "jvm_args"):
            data[key] = runtime[key]
        data["mapping"] = runtime["mapping"]
    validate_properties(data["platform"], properties)
    if render_mode not in ("enhanced", "native", "off") or native_refresh not in ("dirty", "reload"):
        raise ValueError("Unsupported rendering mode or refresh policy")
    if any(p.name not in OPTION_FILES for p in options) or len({p.name for p in options}) != len(options):
        raise ValueError("Only distinct Minecraft options files may be supplied")
    profile.mkdir(parents=True)  # A fresh directory prevents stale reports and mod caches.
    mod_dir = profile / "mods"
    native_dir = profile / "natives"
    mod_dir.mkdir()
    native_dir.mkdir()
    if config:
        shutil.copytree(config, profile / "config")
    for file in options:
        shutil.copyfile(file, profile / file.name)
    if (profile / "optionsof.txt").exists() and not (profile / "options.txt").exists():
        # OptiFine's option loader returns early when Minecraft's options file is absent.
        (profile / "options.txt").touch()
    if shader_pack:
        shader_dir = profile / "shaderpacks"
        shader_dir.mkdir()
        shutil.copyfile(shader_pack, shader_dir / shader_pack.name)
        settings = profile / "optionsshaders.txt"
        lines = settings.read_text(encoding="utf-8").splitlines() if settings.exists() else []
        lines = [line for line in lines if not line.startswith("shaderPack=")]
        settings.write_text("\n".join(lines + ["shaderPack=" + shader_pack.name]) + "\n", encoding="utf-8")
    settings_before = configurations(profile)

    installed = []
    baseline = list(map(Path, data["mods"]))
    for jar in baseline + list(mods) + ([api] if api else []):
        if jar.name.startswith("fabric-loader-") or (not is_mod(jar) and jar not in mods):
            continue
        with zipfile.ZipFile(jar) as archive:
            metadata = json.loads(archive.read("fabric.mod.json")) if "fabric.mod.json" in archive.namelist() else {}
        if api and jar in baseline and metadata.get("id") not in ("lvb", "lvb_smoke"):
            continue
        if runtime_manifest:
            if metadata.get("id") == "lvb_smoke":
                continue
        destination = mod_dir / jar.name
        if destination.exists():
            raise ValueError(f"Duplicate mod filename: {jar.name}")
        shutil.copyfile(jar, destination)
        identity = {"file": jar.name, "sha256": digest(jar)}
        if metadata:
            identity.update(id=metadata["id"], version=metadata["version"])
        installed.append(identity)

    for jar in data["natives"]:
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.infolist():
                if entry.filename.endswith((".so", ".dll", ".dylib", ".jnilib")):
                    (native_dir / Path(entry.filename).name).write_bytes(archive.read(entry))

    # Packaged classes must be loaded from the tested mod JARs, including shared adapter helpers.
    bundled = {Path(data["core"]), *map(Path, data.get("bundled", []))}
    classpath = []
    for jar in map(Path, data["classpath"]):
        if (not jar.is_file() or jar in bundled
                or jar.name.startswith(("minecraft-", "dev-launch-injector-"))):
            continue
        if is_mod(jar) and not jar.name.startswith("fabric-loader-"):
            continue
        classpath.append(str(jar))
    classpath.append(data["minecraft"])

    report = profile / "smoke.txt"
    executable = str(Path(java).resolve()) if java and os.path.dirname(java) else java or data["java"]
    command = [executable, "-Xmx1G", f"-Djava.library.path={native_dir}",
               f"-Dorg.lwjgl.librarypath={native_dir}", "-Dfabric.disableErrorGui=true",
               *(["-Djava.awt.headless=true"] if runtime_manifest else []),
               "-Dfabric.development=false", "-Dlog4j2.formatMsgNoLookups=true", *data.get("jvm_args", []),
               f"-Dlvb.smoke.report={report}", f"-Dlvb.renderMode={render_mode}",
               f"-Dlvb.nativeRefresh={native_refresh}", *[f"-D{p}" for p in properties],
               *(["-Dlvb.smoke.missingHook=true"] if missing_hook else []),
               *([f"-Dlvb.smoke.shaderPack={shader_pack.name}"] if shader_pack else []),
               "-cp", os.pathsep.join(classpath), "net.fabricmc.loader.impl.launch.knot.KnotClient",
               "--version", "1.8.9", "--gameDir", str(profile), "--assetsDir", data["assets"],
               "--assetIndex", data["assetIndex"], "--accessToken", "0", "--username", "LVBTest",
               "--userProperties", "{}"]
    result = {"schema": 1, "platform": data["platform"], "java": command[0], "mods": installed,
              "target": data.get("target", data["platform"]), "missing_hook": missing_hook,
              "mapping": data["mapping"], "version": data["version"],
              "runtime_artifacts": [{"file": Path(p).name, "sha256": digest(Path(p))}
                                    for p in classpath + data["natives"]],
              "configuration_before": settings_before, "properties": list(properties),
              "render_mode": render_mode, "native_refresh": native_refresh}
    if shader_pack:
        result["shader_pack"] = {"file": shader_pack.name, "sha256": digest(shader_pack)}
    with (profile / "console.log").open("w", encoding="utf-8") as log:
        try:
            process = subprocess.run(command, cwd=profile, stdout=log, stderr=subprocess.STDOUT, timeout=240)
            result["exit_code"] = process.returncode
        except subprocess.TimeoutExpired:
            result["exit_code"] = -1
            result["failure"] = "Client exceeded 240 seconds"
    result["report"] = report.read_text(encoding="utf-8").strip() if report.exists() else ""
    result["configuration_after"] = configurations(profile)
    if runtime_manifest:
        diagnostic = "LVB mapping generation mismatch: this JAR requires "
        output = (profile / "console.log").read_text(encoding="utf-8", errors="replace")
        result["passed"] = (result["exit_code"] not in (0, -1) and diagnostic in output
                            and "MixinInitialisationError" in output
                            and "Failed to select mixin config" not in output
                            and "ClassNotFoundException: net.minecraft" not in output
                            and "[LWJGL]" not in output and not report.exists())
        result["assertions"] = ["wrong-generation-rejected"] if result["passed"] else []
        (profile / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(f"{'PASS' if result['passed'] else 'FAIL'}: generation guard ({profile / 'result.json'})")
        return 0 if result["passed"] else 1
    client_report = profile / "client.json"
    try:
        result["client"] = json.loads(client_report.read_text(encoding="utf-8")) if client_report.exists() else {}
        failure = validate_client(result)
    except (ValueError, TypeError, AttributeError):
        failure = "Invalid client report"
    if failure:
        result.setdefault("failure", failure)
    result["passed"] = result["exit_code"] == 0 and result["report"].startswith("PASS:") and not result.get("failure")
    (profile / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"{'PASS' if result['passed'] else 'FAIL'}: {profile / 'result.json'}")
    return 0 if result["passed"] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=TARGETS)
    parser.add_argument("--profile", type=Path, required=True, help="New directory for this run")
    parser.add_argument("--mod", type=Path, action="append", default=[], help="Published mod JAR; repeat as needed")
    parser.add_argument("--api", type=Path, help="Published API distribution replacing the baseline API modules")
    parser.add_argument("--config", type=Path, help="Directory of mod configurations to copy")
    parser.add_argument("--options", type=Path, action="append", default=[], help="Minecraft options file; repeat as needed")
    parser.add_argument("--java", help="Java executable; defaults to the build's Java 8 launcher")
    parser.add_argument("--shader-pack", type=Path, help="Shader ZIP to install, select and verify with OptiFine")
    parser.add_argument("--property", action="append", default=[], help="Optional lvb.smoke feature assertion")
    parser.add_argument("--render-mode", choices=("enhanced", "native", "off"), default="enhanced")
    parser.add_argument("--missing-hook", action="store_true", help="Exercise the competing terrain hook fixture")
    parser.add_argument("--reject-runtime", type=Path, help="Launch against another target's manifest; require generation rejection")
    parser.add_argument("--native-refresh", choices=("dirty", "reload"), default="dirty")
    args = parser.parse_args()
    try:
        validate_properties(TARGETS[args.platform]["platform"], args.property)
    except ValueError as failure:
        parser.error(str(failure))
    if args.profile.exists():
        parser.error("The profile directory already exists; choose a fresh directory")
    root = Path(__file__).resolve().parents[1]
    target = TARGETS[args.platform]
    manifest = root / target["platform"] / target["build_directory"] / "mod-compatibility/launch.json"
    return launch(manifest, args.profile.resolve(), [p.resolve() for p in args.mod],
                  args.java, args.property, args.config.resolve() if args.config else None,
                  [p.resolve() for p in args.options], args.shader_pack.resolve() if args.shader_pack else None,
                  args.render_mode, args.native_refresh, args.missing_hook, args.reject_runtime,
                  args.api.resolve() if args.api else None)


if __name__ == "__main__":
    raise SystemExit(main())
