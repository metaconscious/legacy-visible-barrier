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
| `./gradlew testReleaseTools` | SemVer and publisher tests without network publication |
| `./gradlew verifyArtifacts` | Build and inspect both remapped JARs |
| `./gradlew smokeTest` | Start both isolated clients on Java 8 and verify startup, controls, and Mixins |
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

`smokeTest` requires a display; use `xvfb-run -a ./gradlew smokeTest` on a headless Linux
system with Xvfb installed. It uses fresh directories under each adapter's `build`,
exits automatically, and writes `build/reports/smoke-test.txt` in each adapter. CI runs
these checks before staging. Test mod classes and resources are excluded from distributions.

For dependency updates, preserve the intermediary generation and update explicit module
coordinates together. OSL module versions are pinned from the OSL 0.16.3 distribution.
Check both adapters when changing Loom, mappings, resources, or the core. Do not raise
a documented minimum dependency version without applying the compatibility policy.

## In-game smoke test

Run the test for both platforms, including a packaged JAR installed in its matching
launcher environment:

1. Start at the title screen; confirm startup completes without Mixin errors.
2. Open Controls; confirm one toggle binding, initially `B`, in the existing category.
3. Enter a creative singleplayer world and place adjacent barriers.
4. Toggle visibility; confirm the model, translucency, internal-face culling, translated
   feedback, and disappearance of barrier particles while visible.
5. Toggle off; confirm ordinary hidden barriers and vanilla particle behavior return.
6. Rebind the key, reload resources, change worlds, and reconnect. Confirm the binding
   works and visibility is retained. Restart the client and confirm visibility is hidden.

Keep reports concise: platform, Minecraft/Java/loader/API versions, reproduction steps,
expected behavior, and relevant crash-report or log excerpts. Do not include account
tokens or unrelated logs.
