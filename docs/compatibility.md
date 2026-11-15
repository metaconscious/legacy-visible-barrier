# Compatibility

## Supported environments

Both artifacts target the Minecraft **1.8.9 client**, using Java 8 bytecode. Java 8 is
the runtime baseline. Building uses JDK 21 and runs the core tests on JDK 8.

| Artifact | Mapping environment | Loader used to build | Required API |
| --- | --- | --- | --- |
| `+legacy-fabric` | Legacy Fabric intermediary generation 1; Yarn build 568 | Fabric Loader 0.16.13 | Legacy Fabric API 1.11.1+1.8.9 or compatible later version |
| `+ornithe` | Calamus generation 1; Feather build 28 | Fabric Loader 0.18.1 | OSL 0.16.3, including keybinds 0.1.3 and lifecycle-events 0.5.6 |

Legacy Fabric metadata retains its existing loader minimum of 0.14.19. Ornithe metadata
requires loader 0.18.1 and declares OSL core 0.6.0, entrypoints 0.4.3, keybinds 0.1.3,
lifecycle-events 0.5.6, and resource-loader 0.5.4. Install the API distribution matching the selected
mapping environment. Quilt and generation 2 are outside this release's support matrix.

The mod is client-only. It does not change collision, server state, or world data. Both
artifacts have the same mod ID and are alternative installations.

## Stable user-facing contract

| Surface | Guarantee |
| --- | --- |
| Mod ID | `lvb` |
| Default control | `B`, rebindable in Minecraft Controls |
| Binding key | `key.toggle_barrier_visibility` |
| Category key | `key.category.legacy_visible_barriers` |
| Feedback keys | `lvb.to_visible`, `lvb.to_invisible` |
| Resource namespace | `assets/lvb`, with barrier assets under `assets/minecraft` |
| Translations | German, English, Spanish, French, Japanese, Korean, Russian, Simplified Chinese, Traditional Chinese |
| Initial state | Hidden on client startup |
| State lifetime | Retained across worlds in the same process; not saved to disk |
| Visible state | Translucent barrier block model, adjacent barrier faces culled, barrier particles suppressed |
| Input outside a world | Consumed without changing state or sending feedback |
| Metadata | Client environment, explicit game/Java/API requirements, CC0-1.0 license |

## Semantic versioning

Releases follow [Semantic Versioning 2.0.0](https://semver.org/):

- **Major:** incompatible changes to the documented public contract, including a mod ID
  change, removal of supported environments, or an increased required runtime baseline.
- **Minor:** compatible functionality, deprecations, or substantial internal improvements.
- **Patch:** compatible fixes.

`mod_version` is the single release version, such as `1.2.0`. Artifact versions add build
metadata: `1.2.0+legacy-fabric` and `1.2.0+ornithe`. These have equal SemVer precedence.
The full artifact version is identical in the filename, mod metadata, and release manifest.
The Git tag is `v1.2.0`. A prerelease uses, for example, `1.3.0-rc.1+ornithe`.

Build metadata identifies the platform; it is not a substitute for a version increment
when fixing a published binary. Published tags and artifacts are immutable. Internal
Java packages and build task implementation details are not public extension APIs.
