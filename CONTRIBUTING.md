# Contributing

## Development setup

Install Java 8 or newer to launch the wrapper, and Python 3.10 or newer. The committed
`gradle/gradle-daemon-jvm.properties` selects JDK 21 for the Gradle daemon. Compilation
also uses JDK 21; tests and development clients use JDK 8.

Gradle detects installed JDKs first and provisions missing versions into its user home
(`~/.gradle/jdks` by default). The root and included `build-logic` builds both configure
the Foojay toolchain resolver. The first build requires network access. For preinstalled
toolchains, `LVB_BUILD_JDK` and `LVB_TEST_JDK` can identify their installation directories.
CI also uses the standard `JAVA_HOME_21_X64` and `JAVA_HOME_8_X64` variables.

```sh
gh repo clone metaconscious/legacy-visible-barrier
cd legacy-visible-barrier
./gradlew clean build
```

Use `gradlew.bat` on Windows. The build uses Python's `python3` command. The wrapper pins
Gradle 8.14.7 and verifies the distribution checksum. Loom 1.11.8, Legacy Looming 1.11.8,
and Ploceus 1.11.6 share the JDK 21 build runtime; mod classes compile with `--release 8`.

### IntelliJ IDEA

Open the repository as a Gradle project and select the Gradle wrapper as its distribution.
IntelliJ IDEA 2025.1 and newer supports
[daemon JVM criteria](https://www.jetbrains.com/help/idea/gradle.html) by default.
Reload the Gradle project after changing build settings; the Gradle JVM criteria should
show Java 21. Delegate builds and tests to Gradle so the compiler, test runtime,
resource processing, and remapping use the project's configuration.

To refresh the daemon JDK download URLs while retaining Java 21, run
`./gradlew updateDaemonJvm --jvm-version=21` and commit the generated properties file.

Useful tasks:

| Command | Purpose |
| --- | --- |
| `./gradlew :core:test` | Domain and lifecycle tests on Java 8 |
| `./gradlew testReleaseTools` | Release, artifact and compatibility-runner script tests |
| `./gradlew verifyArtifacts` | Build and inspect both remapped JARs |
| `./gradlew smokeTest` | Verify both Java 8 clients in enhanced, native, off and missing-hook profiles |
| `./gradlew :legacy-fabric:runClient` | Legacy Fabric development client |
| `./gradlew :ornithe:runClient` | Ornithe development client |
| `./gradlew stageRelease` | Stage verified JARs and a source/hash manifest from a clean checkout |

Final installable JARs are in each adapter's `build/libs` directory. Development and
source JARs are not installation artifacts. `build/distributions` contains the exact
release files after staging. Artifact checks write `build/reports/artifacts.json`.

## Changes and tests

Keep state and use-case logic in `core`. Platform imports belong in their adapter.
Keep Mixins focused on the mapped hook they implement. Shared textures, models, and
translations belong in `shared-resources`. See [architecture](docs/architecture.md)
and the [public compatibility contract](docs/compatibility.md).

Test observable behavior through the controller and `ClientEffects` boundary. Release
tests use a fake Modrinth service and temporary files. Extend artifact checks when
changing metadata or packaging. Run `./gradlew clean build` before submitting changes.
Validate workflow changes with `actionlint`.

`smokeTest` requires a display. On headless Linux, install Xvfb, `xrandr`, and Mesa OpenGL
drivers, then run `LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a ./gradlew smokeTest`.
It uses fresh directories under each adapter's `build`,
exits automatically, and writes the enhanced report to `build/reports/smoke-test.txt`
and `client.json` in each adapter. Other profiles write to `build/reports/nativeSmokeTest`,
`offSmokeTest` and `missingHookSmokeTest`. CI runs these checks before staging. Test mod classes and resources are excluded from distributions.

Client tests compare barrier pixels and water/glass blending against a shared reference
batch, count geometry and terrain invalidations, exercise
chunk/world/resource updates, and verify OpenGL state restoration and failure recovery.
VBO and display-list terrain and mono/anaglyph camera passes are covered. Native checks
verify that enhanced hooks are absent, barrier pixels and compiled meshes follow the
toggle, and cancelling clicks cause no refresh. Separate fixtures cover a competing
terrain redirect, contained failure handover and unavailable/off behavior.
To exercise the explicit full-reload option, run
`./gradlew :legacy-fabric:runNativeSmokeTest :ornithe:runNativeSmokeTest -PlvbNativeRefresh=reload`. Optional mod fixtures
check active overlays across toggles and reloads. See [compatibility.md](docs/compatibility.md)
for the tested combinations.

### Production mod compatibility tests

Use remapped production JARs for third-party mods, including OptiFabric, which replaces
game classes in the production namespace. Prepare the matching adapter, then supply
published mod JARs and their dependencies to the isolated runner:

```sh
./gradlew :legacy-fabric:prepareModCompatibility :ornithe:prepareModCompatibility
python3 tools/test_mod_compatibility.py legacy-fabric \
  --profile build/mod-tests/chunk-borders \
  --mod /path/to/legacy-chunk-borders-1.0.0+1.8.2-1.8.9.jar \
  --property lvb.smoke.chunkBorders=true
```

Each `--profile` must be a new directory. Repeat `--mod` for additional JARs; the
runner supplies LVB, its test mod and the adapter's baseline APIs. Add optional OSL
modules when another mod requires them. `--config` copies a directory of mod settings
into the profile, and `--java` selects a different Java executable for mods requiring
a newer runtime. Use `--options /path/to/optionsof.txt` and repeat it for `options.txt`
or `optionsshaders.txt` to seed Minecraft/OptiFine settings. The runner uses the same
display requirements as `smokeTest`. Reports contain logs, artifact/configuration hashes,
runtime versions and completed checks. Profiles under `build/` are ignored by Git.
Re-run the Gradle preparation after changing source code or dependencies. Use
`--render-mode native` for the independent native profile and `--native-refresh reload`
to exercise its full-reload option. The default is enhanced mode with dirty native
refresh during recovery. The runner explicitly selects the requested mode and requires
its corresponding client assertions.

With OptiFine/OptiFabric installed, `--shader-pack /path/to/pack.zip` copies and selects
an external shader pack. The smoke test requires that pack and a shader program to be
active in the main translucent pass. The report records the pack hash. Supply
`optionsof.txt` with Fast Render disabled for this profile.

Optional assertions exercise these pinned builds; a missing mod or inactive feature
fails the corresponding check:

| Property | Feature and fixture |
| --- | --- |
| `lvb.smoke.chunkBorders=true` | Enables Legacy Chunk Borders 1.0.0 and requires grid drawing. |
| `lvb.smoke.expectHitboxes=true` | Requires Legacy Hitbox 1.0.0 to draw the fixture player. Enable it in `legacyhitbox.json`. |
| `lvb.smoke.expectHighlights=true` | Requires Legacy Block Highlight 1.0.0 to draw the selected barrier. Enable it in `legacyblockhighlight.json`. |
| `lvb.smoke.orthographic=true` | Requires orthographic projection. Enable Legacy OrthoCamera 1.0.1 in `orthocamera.json` with `save_enabled_state=true`, `auto_third_person=false` and `fixed=false`; retain its scale and clipping-distance defaults. |
| `lvb.smoke.axolotlOutlines=true` | Enables AxolotlClient 3.0.6 (Legacy Fabric) or 3.1.8 (Ornithe) custom outlines and verifies the resulting line width. |
| `lvb.smoke.villages=true` | Supplies a village to Kabo Village Marker 1.3.0 and requires drawing. |
| `lvb.smoke.waypoints=true` | Supplies an in-view world label to AxolotlClient Waypoints 0.0.3 and requires drawing. |
| `lvb.smoke.miningProgress=true` | Adds active block-mining progress in Ornithe to exercise mods hooked into that rendering stage. |

Third-party JARs are supplied locally and excluded from CI and release artifacts.

For dependency updates, preserve the intermediary generation and update explicit module
coordinates together. OSL module versions are pinned from the OSL 0.16.3 distribution.
Check both adapters when changing Loom, mappings, resources, or the core. Do not raise
a documented minimum dependency version without applying the compatibility policy.

## In-game smoke test

Run the test for both platforms, including a packaged JAR installed in its matching
launcher environment:

1. Start at the title screen; confirm startup completes without Mixin errors.
2. Open Controls; confirm one toggle binding, initially `B`, in the existing category.
3. Enter a creative singleplayer world and place adjacent barriers, including a pair
   across a chunk boundary. Place stone in front of another barrier.
4. Toggle visibility; confirm the model, translucency, internal-face culling, translated
   feedback, and disappearance of barrier particles while visible.
   Toggle repeatedly while moving and turning. Enhanced mode must leave terrain stable;
   native dirty refresh must converge without discarding existing meshes. Full reload
   can temporarily clear terrain. The barrier behind stone must remain occluded.
5. Toggle off; confirm ordinary hidden barriers and vanilla particle behavior return.
6. Rebind the key, reload resources, change worlds, and reconnect. Confirm the binding
   works and visibility is retained. Restart the client and confirm visibility is hidden.

Keep reports concise: platform, Minecraft/Java/loader/API versions, reproduction steps,
expected behavior, and relevant crash-report or log excerpts. Do not include account
tokens or unrelated logs.
