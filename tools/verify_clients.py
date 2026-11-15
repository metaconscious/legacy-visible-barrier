"""Run the existing client contracts against all production distributions."""

import argparse
import json
from pathlib import Path

from test_mod_compatibility import launch
from versions import TARGETS


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("build/client-tests"), help="New directory for reports")
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Choose a new output directory")
    root = Path(__file__).resolve().parents[1]
    manifests = {target: root / info["platform"] / info["build_directory"] / "mod-compatibility/launch.json"
                 for target, info in TARGETS.items()}
    for manifest in manifests.values():
        if not manifest.is_file():
            parser.error(f"Run prepareModCompatibility first: {manifest}")
    output = args.output.resolve()
    results = []
    for target, manifest in manifests.items():
        for mode in ("enhanced", "native", "off", "missing-hook"):
            profile = output / target / mode
            status = launch(manifest, profile, [], render_mode="enhanced" if mode == "missing-hook" else mode,
                            missing_hook=mode == "missing-hook")
            results.append({"target": target, "profile": mode, "passed": status == 0})
    for target, manifest in manifests.items():
        data = json.loads(manifest.read_text(encoding="utf-8"))
        if "menu_mods" not in data:
            continue
        status = launch(manifest, output / target / "mod-menu", list(map(Path, data["menu_mods"])))
        results.append({"target": target, "profile": "mod-menu", "passed": status == 0})
        if "menu_additional" in data:
            extra = data["menu_additional"]
            status = launch(manifest, output / target / "mod-menu-newer", list(map(Path, extra["mods"])),
                            api=list(map(Path, extra["api"])))
            results.append({"target": target, "profile": "mod-menu-newer", "passed": status == 0})
        if "quilt" in data.get("runtimes", {}):
            for mode in ("enhanced", "native", "off", "missing-hook"):
                status = launch(manifest, output / target / ("quilt-" + mode),
                                list(map(Path, data["menu_mods"])), loader="quilt",
                                render_mode="enhanced" if mode == "missing-hook" else mode,
                                missing_hook=mode == "missing-hook")
                results.append({"target": target, "profile": "quilt-" + mode, "passed": status == 0})
    for target, runtime in (("ornithe", "ornithe.gen2"), ("ornithe.gen2", "ornithe")):
        status = launch(manifests[target], output / target / "wrong-generation", [], runtime_manifest=manifests[runtime])
        results.append({"target": target, "profile": "wrong-generation", "passed": status == 0})
    (output / "summary.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    return 0 if all(result["passed"] for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
