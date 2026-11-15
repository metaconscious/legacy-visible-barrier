"""Verify the installable, remapped distributions using only the Python standard library."""

import argparse
import hashlib
import json
import re
import struct
import zipfile
from pathlib import Path

from versions import TARGETS, artifact_version, runtime_dependencies, target_metadata, target_paths

CORE_PACKAGE = "io/github/metaconscious/lvb/core/"
LOCALES = {"de_DE", "en_US", "es_ES", "fr_FR", "ja_JP", "ko_KR", "ru_RU", "zh_CN", "zh_TW"}
TRANSLATION_KEYS = {
    "key.category.legacy_visible_barriers",
    "key.toggle_barrier_visibility",
    "lvb.to_visible",
    "lvb.to_invisible",
    "lvb.mode_native",
    "lvb.unavailable",
    "lvb.disabled",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def class_constants(data):
    """Read constant types separately so annotation strings are not mistaken for JVM references."""
    count = struct.unpack_from(">H", data, 8)[0]
    offset, index = 10, 1
    constants, class_indexes, descriptor_indexes = {}, [], []
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
             12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            constants[index] = data[offset:offset + length].decode("utf-8", errors="replace")
            offset += length
        else:
            require(tag in sizes, f"Unknown JVM constant-pool tag {tag}")
            if tag == 7:
                class_indexes.append(struct.unpack_from(">H", data, offset)[0])
            elif tag == 12:
                descriptor_indexes.append(struct.unpack_from(">H", data, offset + 2)[0])
            elif tag == 16:
                descriptor_indexes.append(struct.unpack_from(">H", data, offset)[0])
            offset += sizes[tag]
            if tag in (5, 6):
                index += 1
        index += 1
    return constants, class_indexes, descriptor_indexes, offset


def class_strings(data):
    return class_constants(data)[0].values()


def class_type_references(data):
    constants, classes, descriptors, offset = class_constants(data)
    offset += 6  # access flags, this class, superclass
    interfaces = struct.unpack_from(">H", data, offset)[0]
    offset += 2 + interfaces * 2
    for _ in range(2):  # declared fields and methods
        count = struct.unpack_from(">H", data, offset)[0]
        offset += 2
        for _ in range(count):
            _, _, descriptor, attributes = struct.unpack_from(">4H", data, offset)
            descriptors.append(descriptor)
            offset += 8
            for _ in range(attributes):
                length = struct.unpack_from(">I", data, offset + 2)[0]
                offset += 6 + length
    return [constants[index] for index in classes + descriptors]


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


def verify_mapping_references(classes, refmap, mappings):
    lines = mappings.read_text(encoding="utf-8").splitlines()
    header = lines[0].split("\t")
    require(header[:3] == ["tiny", "2", "0"] and "intermediary" in header[3:], "Unexpected intermediary mappings")
    column = 1 + header[3:].index("intermediary")
    known = {parts[column] for line in lines[1:] if (parts := line.split("\t"))[0] == "c"}
    references = set()

    def inspect(value):
        if isinstance(value, dict):
            for child in value.values():
                inspect(child)
        elif isinstance(value, str):
            references.update(re.findall(r"(?:L|^)((?:net/minecraft|com/mojang/blaze3d)/[A-Za-z0-9_$/]+)", value))

    for data in classes.values():
        for value in class_type_references(data):
            inspect(value)
    inspect(refmap)
    require(references, "Distribution contains no remapped Minecraft references")
    require(not references - known, "Unknown Minecraft mapping references: " + ", ".join(sorted(references - known)))


def verify_jar(path, target, base, core_classes, assets, mappings=None):
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
        require(metadata["depends"] == runtime_dependencies(target), "Runtime dependency contract changed")
        require(metadata.get("custom", {}).get("lvb:target") == target_metadata(target), "Mapping target metadata mismatch")
        manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8").replace("\r\n ", "")
        generation_key = "Legacy-Fabric-Intermediary-Version" if target == "legacy-fabric" else "Calamus-Generation"
        require(re.search(r"^" + generation_key + ": " + str(TARGETS[target]["generation"]) + r"\r?$", manifest, re.M),
                "Remapped JAR generation mismatch")

        config_name = f"lvb-{TARGETS[target]['platform']}.mixins.json"
        require(metadata["mixins"] == [config_name], "Mixin config mismatch")
        mixins = json.loads(jar.read(config_name))
        require(mixins["required"] and mixins["compatibilityLevel"] == "JAVA_8", "Mixin requirements changed")
        require(not mixins.get("mixins") and not mixins.get("server"), "Mixins must be client-only")
        expected_mixins = {"BarrierBlockMixin", "BarrierParticleMixin", "ChunkMixin", "GameRendererMixin",
                           "WorldRendererMixin", "ChunkRenderListAccess", "TranslucentMeshMixin", "BufferBuilderMixin", "VertexStateMixin",
                           "BlockStateMapperMixin" if target == "legacy-fabric" else "BlockModelsMixin"}
        require(set(mixins["client"]) == expected_mixins, "Unexpected client Mixins")
        require(mixins.get("plugin") == adapter_package.replace("/", ".") + "RenderingPlugin",
                "Rendering profile plugin is missing")
        require(adapter_package + "RenderingPlugin.class" in classes, "Rendering profile plugin class is missing")
        require(mixins["injectors"]["defaultRequire"] == 1, "Structural Mixins must remain required")
        for mixin in expected_mixins:
            require(adapter_package + "mixin/" + mixin + ".class" in classes, f"Missing Mixin {mixin}")
        refmap = json.loads(jar.read(mixins["refmap"]))
        require(mixins["refmap"] == f"lvb-{target}.refmap.json", "Refmap target mismatch")
        require(refmap.get("mappings"), "Remapped Mixin references are missing")
        if mappings:
            verify_mapping_references(classes, refmap, mappings)

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
            "asset_count": len(actual_assets), "class_major_version": 52, "generation": TARGETS[target]["generation"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-version", required=True)
    parser.add_argument("--core", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--artifact", action="append", required=True, help="TARGET=JAR; repeat for each target")
    parser.add_argument("--mappings", action="append", required=True, help="TARGET=TINY; repeat for each target")
    args = parser.parse_args()
    jars, mappings = target_paths(args.artifact), target_paths(args.mappings)
    require(set(jars) == set(mappings), "Each artifact needs its intermediary mappings")
    core = verify_core(args.core)
    reports = [verify_jar(jar, target, args.base_version, core, args.assets, mappings[target])
               for target, jar in jars.items()]
    require(len({report["core_sha512"] for report in reports}) == 1,
            "Embedded core differs between mapping environments")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(reports, indent=2) + "\n", encoding="utf-8")
    for report in reports:
        print(f"Verified {report['filename']}: Java 8, {report['core_class_count']} core classes, "
              f"{report['asset_count']} shared assets, client metadata and Mixin references")


if __name__ == "__main__":
    main()
