# Legacy Visible Barrier

A client mod for Minecraft 1.8.9 that renders barrier blocks when visibility is enabled.
Press **B** to toggle, or rebind the key in **Options → Controls**. A translated chat
message reports visibility or rendering status.

## Installation

Download the artifact matching your platform and mapping generation from
[GitHub Releases](https://github.com/metaconscious/legacy-visible-barrier/releases),
and put it in the instance's `mods` directory with its required API. The existing
[Modrinth project](https://modrinth.com/mod/legacy-visible-barrier) distributes Gen1 builds.

| Platform | Artifact suffix | Required API |
| --- | --- | --- |
| Legacy Fabric, intermediary generation 1 | `+legacy-fabric.jar` | [Legacy Fabric API](https://modrinth.com/mod/legacy-fabric-api), 1.11.1+1.8.9 or compatible later version |
| Ornithe, Calamus generation 1 | `+ornithe.jar` | [Ornithe Standard Libraries](https://modrinth.com/mod/osl), 0.16.3 |
| Ornithe, Calamus generation 2 | `+ornithe.gen2.jar` | [Ornithe Standard Libraries Gen2](https://modrinth.com/mod/osl/version/PSPwlyvh), 0.17.1 or compatible later Gen2 version |
| Forge 11.15.1.2318 | `+forge.jar` | None; Mixin is included |

Ornithe artifacts also support Quilt Loader 0.30.2 with the matching generation and OSL.

Install one variant. All use mod ID `lvb`. Java 8 is the runtime baseline, and no server
installation is needed. See the [compatibility matrix](docs/compatibility.md) for loader
versions and supported environments. Releases before 1.2.0 use their historical filenames.

Barriers start hidden each time the client launches. Visibility is retained when changing
worlds or reconnecting within that process. Visible barriers use a translucent block model
and suppress the ordinary barrier particles. Collision and world data remain unchanged.
The default enhanced renderer shares the draw order of translucent terrain, including
water and glass, without rebuilding terrain when toggled. A native compatibility mode
uses the installed terrain renderer and rebuilds sections as visibility changes. See
[rendering modes and recovery](docs/compatibility.md#rendering-modes) for configuration.

## Settings

With a matching [Mod Menu](docs/compatibility.md#mod-menu) installed, open **Mods →
Legacy Visible Barrier → Configure**. On Forge, use **Mods → Legacy Visible Barrier → Config**.
The same screen is available by assigning **Open LVB Settings**
in Minecraft Controls and use it in a world. This binding starts unassigned; **B**
continues to toggle visibility.

The screen shows the current renderer, visibility intent and recovery status. Choose
**Automatic**, **Compatibility** or **Disabled** for the next launch. **Advanced**
selects gradual section updates or a full reload for compatibility rendering; full
reloads can flicker. **Save** writes the preferences, **Cancel** discards edits, and
**Reset** restores defaults in the draft. Restart Minecraft to apply saved changes.
Launch options remain authoritative and are identified on the screen.

Preferences remain in `config/lvb.properties`; see
[rendering modes](docs/compatibility.md#rendering-modes) for file and JVM options.

## Development

Install JDK 21 and Python 3.10+, then run:

```sh
./gradlew clean build
```

Gradle selects JDK 21 for the build and JDK 8 for tests and development clients. Missing
JDKs are downloaded automatically and cached in Gradle's user home. The first build
requires network access. IntelliJ IDEA uses the same settings when the project is
imported with the Gradle wrapper.
Both Ornithe generations are available in the same import as `ornithe:gen1` and
`ornithe:gen2`, each with its own Minecraft mappings, OSL dependencies and client task.

The build produces separate remapped JARs in `legacy-fabric/build/libs`,
`ornithe/gen1/build/libs`, `ornithe/gen2/build/libs` and `forge/build/libs`. The projects share their core
logic and assets. Read
[CONTRIBUTING.md](CONTRIBUTING.md) for development commands and testing,
[architecture](docs/architecture.md) for module boundaries, and
[releasing](docs/releasing.md) for publishing.

## License and credits

LVB is released under [CC0-1.0](LICENSE). The Forge distribution also contains upstream
Mixin 0.8.7 under its MIT license, included at `META-INF/licenses/mixin.txt`.

Created by Mark Oven, with the Ornithe port contributed by
[DaveyL2013](https://github.com/DaveyL2013). Inspired by
[VisibleBarriers](https://github.com/olvend/VisibleBarriers) by olvend.
