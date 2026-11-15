# Contributing

## Development setup

Install JDK 21 and Python 3.10 or newer. The committed
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
Gradle 9.2.1 and verifies the distribution checksum. Loom 1.15.5, Legacy Looming 1.15.3,
and Ploceus 1.15.9 share the JDK 21 build runtime; mod classes compile with `--release 8`.

### IntelliJ IDEA

Open the repository as a Gradle project and select the Gradle wrapper as its distribution.
IntelliJ IDEA 2025.1 and newer supports
[daemon JVM criteria](https://www.jetbrains.com/help/idea/gradle.html) by default.
Reload the Gradle project after changing build settings; the Gradle JVM criteria should
show Java 21. Delegate builds and tests to Gradle so the compiler, test runtime,
resource processing, and remapping use the project's configuration.

Both `ornithe:gen1` and `ornithe:gen2` are imported together. Each owns its Java sources,
Minecraft mappings and OSL classpath. Run `:ornithe:gen1:runClient` or
`:ornithe:gen2:runClient` from the Gradle tool window to use the provisioned Java 8 runtime.
Loom also generates separate **Minecraft Client** application configurations for each
generation. Reload Gradle after updating an existing checkout to this layout.

To refresh the daemon JDK download URLs while retaining Java 21, run
`./gradlew updateDaemonJvm --jvm-version=21` and commit the generated properties file.

Useful tasks:

| Command | Purpose |
| --- | --- |
| `./gradlew :core:test` | Domain, lifecycle and settings-draft tests on Java 8 |
| `./gradlew :legacy-fabric:test :ornithe:test` | Configuration persistence tests on Java 8 |
| `./gradlew testReleaseTools` | Release, artifact and compatibility-runner script tests |
| `./gradlew verifyArtifacts` | Build and inspect all three remapped JARs |
| `./gradlew verifyDistributions` | Alias for all-distribution artifact verification |
| `./gradlew prepareModCompatibility` | Prepare all three production client fixtures |
| `./gradlew smokeTest` | Verify all three Java 8 development clients in enhanced, native, off and missing-hook profiles |
| `./gradlew :legacy-fabric:runClient` | Legacy Fabric development client |
| `./gradlew :ornithe:gen1:runClient` | Ornithe Gen1 development client |
| `./gradlew :ornithe:gen2:runClient` | Ornithe Gen2 development client |
| `./gradlew stageRelease` | Stage verified JARs and a source/hash manifest from a clean checkout |

Final installable JARs are in `legacy-fabric/build/libs`, `ornithe/gen1/build/libs` and
`ornithe/gen2/build/libs`. Development and
source JARs are not installation artifacts. `build/distributions` contains the exact
release files after staging. Artifact checks write `build/reports/artifacts.json`
for all three targets.

The root build compiles both Ornithe generations in one invocation. To build one,
use `:ornithe:gen1:build` or `:ornithe:gen2:build`. Minecraft integration lives in
each generation's `src/main/java` and `src/smokeTest/java` directories. Shared bootstrap
code and test helpers live in `ornithe/src/main/java` and `ornithe/src/testFixtures/java`;
these source sets have no Minecraft dependency.

## Changes and tests

Keep state and use-case logic in `core`. Platform imports belong in their adapter.
Keep Mixins focused on the mapped hook they implement. Shared textures, models, and
translations belong in `shared-resources`. See [architecture](docs/architecture.md)
and the [public compatibility contract](docs/compatibility.md).

Test observable behavior through the controller and `ClientEffects` boundary. Release
tests use a fake GitHub CLI and temporary files. Extend artifact checks when
changing metadata or packaging. Run the full verification sequence below before submitting changes.
Validate workflow changes with `actionlint`.

`smokeTest` requires a display. On headless Linux, install Xvfb, `xrandr`, and Mesa OpenGL
drivers, then run `LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a ./gradlew smokeTest`.
It uses fresh directories under each target's build directory,
exits automatically, and writes the enhanced report to `reports/smoke-test.txt`
and `reports/client.json`. Other profiles write to `reports/nativeSmokeTest`,
`offSmokeTest` and `missingHookSmokeTest`. Test mod classes and resources are excluded
from distributions.

Full verification builds all distributions, then runs the same client contracts
against all three production JARs. Four additional Mod Menu profiles verify native settings through the published provider
APIs; two negative launches require early rejection of the wrong Ornithe generation. CI runs this sequence before staging:

```sh
./gradlew clean build prepareModCompatibility
python3 tools/verify_clients.py --output build/client-tests
./gradlew verifyDistributions
```

The client runner requires a new output directory and a display; on headless Linux,
prefix its command with `LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a`. Gen2 launch manifests
include the package-access flag used by the official Ornithe launcher profile.

Settings checks cover unbound/idempotent registration, title and world entry, keyboard
editing, resize, persistence, parent return and preservation of runtime recovery.
File persistence tests also run on Windows in CI.

Client tests compare barrier pixels and water/glass blending against a shared reference
batch, count geometry and terrain invalidations, exercise
chunk/world/resource updates, and verify OpenGL state restoration and failure recovery.
VBO and display-list terrain and mono/anaglyph camera passes are covered. Native checks
verify that enhanced hooks are absent, barrier pixels and compiled meshes follow the
toggle, and cancelling clicks cause no refresh. Separate fixtures cover a competing
terrain redirect, contained failure handover and unavailable/off behavior.
To exercise the explicit full-reload option, run
`./gradlew :legacy-fabric:runNativeSmokeTest :ornithe:gen1:runNativeSmokeTest :ornithe:gen2:runNativeSmokeTest -PlvbNativeRefresh=reload`. Optional mod fixtures
check active overlays across toggles and reloads. See [compatibility.md](docs/compatibility.md)
for the tested combinations.

### Production mod compatibility tests

Use remapped production JARs for third-party mods, including OptiFabric, which replaces
game classes in the production namespace. Prepare the matching adapter, then supply
published mod JARs and their dependencies to the isolated runner:

```sh
./gradlew prepareModCompatibility
python3 tools/test_mod_compatibility.py legacy-fabric \
  --profile build/mod-tests/chunk-borders \
  --mod /path/to/legacy-chunk-borders-1.0.0+1.8.2-1.8.9.jar \
  --property lvb.smoke.chunkBorders=true
```

Select `legacy-fabric`, `ornithe` or `ornithe.gen2` as the runner's first argument.
Each `--profile` must be a new directory. Repeat `--mod` for additional JARs; the
runner supplies LVB, its test mod and the adapter's baseline APIs. Add optional OSL
modules when another mod requires them. To test a newer complete API distribution,
use `--api /path/to/ornithe-standard-libraries.jar`; it replaces the baseline modules
so their direct dependencies do not override the bundle's versions. Repeat `--api`
to supply a complete set of individual replacement API modules.
`--config` copies a directory of mod settings
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
| `lvb.smoke.axolotlOutlines=true` | Enables AxolotlClient custom outlines and verifies the resulting line width. |
| `lvb.smoke.villages=true` | Supplies a village to Kabo Village Marker and requires drawing. |
| `lvb.smoke.waypoints=true` | Supplies an in-view world label to AxolotlClient Waypoints and requires drawing. |
| `lvb.smoke.miningProgress=true` | Adds active block-mining progress in Ornithe to exercise mods hooked into that rendering stage. |

The pinned Mod Menu UI profiles run in CI. Other third-party fixtures are supplied
locally. Test mods and their dependencies are excluded from release artifacts.

Fixture versions and their environments are listed in [compatibility.md](docs/compatibility.md).
For dependency updates, edit `gradle/targets.json`, preserving the intermediary generation
and updating explicit module coordinates together. OSL baselines are 0.16.3 for Gen1
and 0.17.1 for Gen2. Check all three distributions when changing Loom, mappings, resources, or the core. Do not raise
a documented minimum dependency version without applying the compatibility policy.

## In-game smoke test

Run the test for each target, including a packaged JAR installed in its matching
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
6. Open settings from Mod Menu at the title screen and from the settings binding in a
   world. Change preferences, visit Controls and resize the window; retain the draft.
   Use Tab/Shift-Tab and Enter/Space at a 320×240 logical viewport, all GUI scales,
   and with long and CJK translations. Verify Save, Cancel/Escape, Reset and return
   to the invoking screen. Confirm status reflects recovery and launch overrides.
7. Rebind the key, reload resources, change worlds, and reconnect. Confirm the binding
   works and visibility is retained. Restart the client and confirm visibility is hidden.

Keep reports concise: platform, Minecraft/Java/loader/API versions, reproduction steps,
expected behavior, and relevant crash-report or log excerpts. Do not include account
tokens or unrelated logs.
