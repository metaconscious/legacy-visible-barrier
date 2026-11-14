# Legacy Visible Barrier

A client mod for Minecraft 1.8.9 that renders barrier blocks when visibility is enabled.
Press **B** to toggle, or rebind the key in **Options → Controls**. A translated chat
message reports visibility or rendering status.

## Installation

Download the artifact matching your platform from
[Modrinth](https://modrinth.com/mod/legacy-visible-barrier) or
[GitHub Releases](https://github.com/metaconscious/legacy-visible-barrier/releases),
and put it in the instance's `mods` directory with its required API.

| Platform | Artifact suffix | Required API |
| --- | --- | --- |
| Legacy Fabric, intermediary generation 1 | `+legacy-fabric.jar` | [Legacy Fabric API](https://modrinth.com/mod/legacy-fabric-api), 1.11.1+1.8.9 or compatible later version |
| Ornithe, Calamus generation 1 | `+ornithe.jar` | [Ornithe Standard Libraries](https://modrinth.com/mod/osl), 0.16.3 |

Install one variant. Both use mod ID `lvb`. Java 8 is the runtime baseline, and no server
installation is needed. See the [compatibility matrix](docs/compatibility.md) for loader
versions and supported environments. Releases before 1.2.0 use their historical filenames.

Barriers start hidden each time the client launches. Visibility is retained when changing
worlds or reconnecting within that process. Visible barriers use a translucent block model
and suppress the ordinary barrier particles. Collision and world data remain unchanged.
The default enhanced renderer shares the draw order of translucent terrain, including
water and glass, without rebuilding terrain when toggled. A native compatibility mode
uses the installed terrain renderer and rebuilds sections as visibility changes. See
[rendering modes and recovery](docs/compatibility.md#rendering-modes) for configuration.

## Development

Install Java 8 or newer to launch the wrapper, and Python 3.10+, then run:

```sh
./gradlew clean build
```

Gradle selects JDK 21 for the build and JDK 8 for tests and development clients. Missing
JDKs are downloaded automatically and cached in Gradle's user home. The first build
requires network access. IntelliJ IDEA uses the same settings when the project is
imported with the Gradle wrapper.

The build produces separate remapped JARs in `legacy-fabric/build/libs` and
`ornithe/build/libs`. The projects share their core logic and assets. Read
[CONTRIBUTING.md](CONTRIBUTING.md) for development commands and testing,
[architecture](docs/architecture.md) for module boundaries, and
[releasing](docs/releasing.md) for publishing.

## License and credits

Released under [CC0-1.0](LICENSE).

Created by Mark Oven, with the Ornithe port contributed by
[DaveyL2013](https://github.com/DaveyL2013). Inspired by
[VisibleBarriers](https://github.com/olvend/VisibleBarriers) by olvend.
