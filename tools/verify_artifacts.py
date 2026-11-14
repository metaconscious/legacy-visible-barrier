"""Verify the installable, remapped distributions using only the Python standard library."""

import argparse
import hashlib
import json
import re
import struct
import zipfile
from pathlib import Path

from versions import GAME_VERSION, TARGETS, artifact_version

CORE_PACKAGE = "io/github/metaconscious/lvb/core/"
LOCALES = {"de_DE", "en_US", "es_ES", "fr_FR", "ja_JP", "ko_KR", "ru_RU", "zh_CN", "zh_TW"}
TRANSLATION_KEYS = {
    "key.category.legacy_visible_barriers",
    "key.toggle_barrier_visibility",
    "lvb.to_visible",
    "lvb.to_invisible",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def class_strings(data):
    """Read UTF-8 constants, including class and descriptor references, from a JVM class file."""
    count = struct.unpack_from(">H", data, 8)[0]
    offset, index, strings = 10, 1, []
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
             12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            strings.append(data[offset:offset + length].decode("utf-8", errors="replace"))
            offset += length
        else:
            require(tag in sizes, f"Unknown JVM constant-pool tag {tag}")
            offset += sizes[tag]
            if tag in (5, 6):
                index += 1
        index += 1
    return strings


def verify_core(core):
    with zipfile.ZipFile(core) as jar:
        classes = {name: jar.read(name) for name in jar.namelist() if name.endswith(".class")}
    verify_core_classes(classes)
    return classes


def verify_core_classes(classes):
    require(CORE_PACKAGE + "VisibilityController.class" in classes, "Core controller is missing")
    for name, data in classes.items():
        require(name.startswith(CORE_PACKAGE), f"Unexpected core class: {name}")
        for constant in class_strings(data):
            references = re.findall(r"L([^;<>]+);", constant)
            if "/" in constant and not any(c in constant for c in "();<> "):
                references.append(constant.lstrip("["))
            for reference in references:
                require(reference.startswith(("java/", CORE_PACKAGE)),
                        f"Core references a non-JDK type: {name}: {reference}")
            require(not any(prefix in constant for prefix in (
                "net.minecraft", "net.fabricmc", "net.legacyfabric", "net.ornithemc", "org.spongepowered"
            )), f"Core contains a platform reference: {name}: {constant}")


def verify_jar(path, target, base, core_classes, assets):
    expected_version = artifact_version(base, target)
    require(path.name == f"legacy-visible-barrier-{expected_version}.jar", f"Unexpected artifact name: {path.name}")
    package = "legacyfabric" if target == "legacy-fabric" else "ornithe"
    entrypoint = "LegacyFabricClient" if target == "legacy-fabric" else "OrnitheClient"
    adapter_package = f"io/github/metaconscious/lvb/{package}/"
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        require(len(names) == len(set(names)), "Duplicate JAR entries")
        require("LICENSE" in names, "License is missing")
        require(not any(name.endswith(".jar") for name in names), "Unexpected nested dependencies")
        classes = {name: jar.read(name) for name in names if name.endswith(".class")}
        require(not any("SmokeTest" in name or "-smoke." in name for name in names),
                "Test mod files leaked into the distribution")
        for name, data in classes.items():
            require(data[:4] == b"\xca\xfe\xba\xbe", f"Invalid class file: {name}")
            require(struct.unpack_from(">H", data, 6)[0] == 52, f"Not Java 8 bytecode: {name}")
            require(name.startswith((CORE_PACKAGE, adapter_package)), f"Foreign or shaded class: {name}")
        embedded_core = {name: data for name, data in classes.items() if name.startswith(CORE_PACKAGE)}
        require(set(embedded_core) == set(core_classes), "Embedded core classes are missing or unexpected")
        verify_core_classes(embedded_core)

        metadata = json.loads(jar.read("fabric.mod.json"))
        require(metadata["schemaVersion"] == 1 and metadata["id"] == "lvb", "Mod identity changed")
        require(metadata["version"] == expected_version, "Artifact/metadata version mismatch")
        require(metadata["environment"] == "client", "Mod must be client-only")
        require(metadata["license"] == "CC0-1.0", "License metadata changed")
        require(metadata["icon"] == "assets/lvb/icon.png", "Icon path changed")
        require(metadata["entrypoints"] == {
            "client": [f"io.github.metaconscious.lvb.{package}.{entrypoint}"]
        }, "Unexpected entrypoints")
        require(adapter_package + entrypoint + ".class" in classes, "Entrypoint class is missing")
        depends = metadata["depends"]
        require(depends["minecraft"] == GAME_VERSION and depends["java"] == ">=8", "Runtime contract changed")
        required = {"legacy-fabric-api"} if target == "legacy-fabric" else {
            "osl-core", "osl-entrypoints", "osl-keybinds", "osl-lifecycle-events", "osl-resource-loader"
        }
        require(set(depends) == required | {"minecraft", "java", "fabricloader"}, "Dependency boundary changed")
        for dependency in depends.values():
            require(dependency not in ("", "*"), "Runtime dependencies need explicit versions")

        config_name = f"lvb-{target}.mixins.json"
        require(metadata["mixins"] == [config_name], "Mixin config mismatch")
        mixins = json.loads(jar.read(config_name))
        require(mixins["required"] and mixins["compatibilityLevel"] == "JAVA_8", "Mixin requirements changed")
        require(not mixins.get("mixins") and not mixins.get("server"), "Mixins must be client-only")
        expected_mixins = {"BarrierBlockMixin", "BarrierParticleMixin", "ChunkMixin", "GameRendererMixin",
                           "BlockStateMapperMixin" if target == "legacy-fabric" else "BlockModelsMixin"}
        require(set(mixins["client"]) == expected_mixins, "Unexpected client Mixins")
        require(mixins["injectors"]["defaultRequire"] == 1, "Mixin injection failures must be fatal")
        for mixin in expected_mixins:
            require(adapter_package + "mixin/" + mixin + ".class" in classes, f"Missing Mixin {mixin}")
        refmap = json.loads(jar.read(mixins["refmap"]))
        require(refmap.get("mappings"), "Remapped Mixin references are missing")

        expected_assets = {"assets/" + p.relative_to(assets).as_posix(): p.read_bytes()
                           for p in assets.rglob("*") if p.is_file()}
        actual_assets = {name for name in names if name.startswith("assets/") and not name.endswith("/")}
        contracted_assets = {
            "assets/lvb/icon.png", "assets/minecraft/blockstates/barrier.json",
            "assets/minecraft/models/block/barrier.json", "assets/minecraft/textures/blocks/barrier.png",
        } | {f"assets/lvb/lang/{locale}.lang" for locale in LOCALES}
        require(actual_assets == contracted_assets, "Public resource paths changed")
        require(actual_assets == set(expected_assets), "Shared assets are missing or duplicated")
        for name, data in expected_assets.items():
            require(jar.read(name) == data, f"Shared resource changed during packaging: {name}")
        languages = {Path(n).stem for n in actual_assets if n.startswith("assets/lvb/lang/")}
        require(languages == LOCALES, "Supported translations changed")
        for locale in LOCALES:
            lines = jar.read(f"assets/lvb/lang/{locale}.lang").decode("utf-8").splitlines()
            keys = [line.split("=", 1)[0] for line in lines if line and not line.startswith("#")]
            require(set(keys) == TRANSLATION_KEYS and len(keys) == len(set(keys)), f"Translation contract: {locale}")
        require("${" not in json.dumps(metadata), "Unexpanded metadata placeholder")
    return {"target": target, "version": expected_version, "filename": path.name,
            "sha512": hashlib.sha512(path.read_bytes()).hexdigest(), "size": path.stat().st_size,
            "class_count": len(classes), "core_class_count": len(core_classes),
            "core_sha512": hashlib.sha512(b"".join(embedded_core[n] for n in sorted(embedded_core))).hexdigest(),
            "asset_count": len(actual_assets), "class_major_version": 52}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-version", required=True)
    parser.add_argument("--core", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    for target in TARGETS:
        parser.add_argument(f"--{target}", type=Path, required=True)
    args = parser.parse_args()
    core = verify_core(args.core)
    reports = [verify_jar(getattr(args, target.replace("-", "_")), target, args.base_version, core, args.assets)
               for target in TARGETS]
    require(len({report["core_sha512"] for report in reports}) == 1,
            "Embedded core differs between mapping environments")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(reports, indent=2) + "\n", encoding="utf-8")
    for report in reports:
        print(f"Verified {report['filename']}: Java 8, {report['core_class_count']} core classes, "
              f"{report['asset_count']} shared assets, client metadata and Mixin references")


if __name__ == "__main__":
    main()
