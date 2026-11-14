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

## Rendering compatibility

The barrier pass draws after translucent terrain. It preserves the vertex-array,
lightmap and colour state it changes. A renderer replacing `GameRenderer` must retain
its translucent terrain call; [architecture.md](architecture.md) describes the hook.

Barrier quads are sorted within their own batch. Intersections with water, glass or
other translucent geometry can blend in the wrong order. Opaque blocks occlude
barriers. External shader packs are untested; barriers do not participate in OptiFine's
terrain shadow pass.

### Tested mod combinations

The following builds were exercised with visible and hidden barriers, both terrain
backends, resource reloads and world changes. Mod versions and enabled features are
part of each combination.

| Environment | Mods | Settings |
| --- | --- | --- |
| Both adapters | OptiFine HD U M5 with OptiFabric [2.3.6+1.3.1-1.13.2 for Legacy Fabric](https://modrinth.com/mod/legacy-optifabric/version/VxbRKwpM) or [2.3.6+b1.7.3-1.14.4 for Ornithe](https://modrinth.com/mod/legacy-optifabric/version/keYvdDsA) | Shaders off with Fast Render off/on; internal shaders with Fast Render off. |
| Legacy Fabric | LegacyEnhance 1.0.0, Phosphor-Legacy 0.1.5+1.8.9, FPS Plus Ver-D, Legacy Hitbox 1.0.0, Legacy Block Highlight 1.0.0 and Extended Ingame Hud 0.1.0 | Default configuration, installed together. |
| Legacy Fabric | Legacy Hitbox 1.0.0, Legacy Block Highlight 1.0.0, LegacyEnhance 1.0.0 and Phosphor-Legacy 0.1.5+1.8.9 | Gradient hitboxes and volume highlights; also with OptiFine/OptiFabric or AxolotlClient 3.0.6. |
| Legacy Fabric | [Legacy Chunk Borders 1.0.0](https://modrinth.com/mod/legacy-chunk-borders/version/hp3oZxHx) | Grid enabled, alone and with OptiFine/OptiFabric. |
| Legacy Fabric | [Legacy OrthoCamera 1.0.1](https://modrinth.com/mod/legacy-orthocamera/version/PtBq0srz) | Orthographic projection, first-person camera and fixed rotation disabled; alone and with OptiFine/OptiFabric. |
| Legacy Fabric | [AxolotlClient 3.0.6](https://modrinth.com/mod/axolotlclient/version/EpBQmzkD) | Default configuration and custom block outlines. |
| Ornithe | [Kabo Village Marker 1.3.0](https://modrinth.com/mod/kabo-village-marker/version/wsVKa3iS) | Village spheres and golem areas; with OptiFine/OptiFabric during active mining. |
| Ornithe | [AxolotlClient 3.1.8](https://modrinth.com/mod/axolotlclient/version/ySfusRPJ), [Waypoints 0.0.3](https://modrinth.com/mod/axolotlclient-waypoints/version/wYncBniX) and [legacy-lwjgl3 1.2.10](https://modrinth.com/mod/moehreag-legacy-lwjgl3/version/QeDzTTIm), Java 21 | Custom outlines and a world waypoint label; also with Kabo Village Marker. |
| Ornithe | [OfftiFine 2.1.1](https://modrinth.com/mod/offtifine/version/6yzzDHuh), OptiFine/OptiFabric and legacy-lwjgl3 1.2.10, Java 21 | Default networking restrictions. |

Kabo requires the Minecraft 1.8.9 variants of OSL networking 0.8.0 and config 0.5.2.
The AxolotlClient/Waypoints combination requires OSL networking and legacy-lwjgl3
newer than 1.2.8. These dependencies are additional to LVB's baseline APIs.

### Known conflicts

- **Kabo with OptiFine M5:** Kabo's [hook](https://github.com/SpaceWalkerRS/kabo-village-marker/blob/v1.3.0/src/main/java/kabopc/village_marker/mixin/GameRendererMixin.java)
  follows mining-progress rendering. OptiFine skips that call when nothing is being
  mined, hiding Kabo's overlay. Use Kabo without OptiFine for continuous markers.
- **Legacy Block Highlight with AxolotlClient custom outlines:** Block Highlight
  replaces vanilla outline drawing and bypasses Axolotl's outline options. Select
  one outline provider.
- **Published Oldium 1.8.9 JAR:** the [release](https://github.com/OldiumMC/oldium/releases/tag/1.8.9)
  bundles `net.fabricmc.api.EnvType` and fails during startup with a loader constraint
  violation. Its packaging must be corrected before use with this loader setup.

See [Contributing](../CONTRIBUTING.md#production-mod-compatibility-tests) for running
checks with supplied mod JARs and configurations.

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
| Toggle rendering | Only the barrier pass changes; no terrain reload, chunk rebuild, or additional non-barrier model rendering |
| Input outside a world | Consumed without changing state or sending feedback |
| Metadata | Client environment, explicit game/Java/API requirements, CC0-1.0 license |

## Semantic versioning

Releases follow [Semantic Versioning 2.0.0](https://semver.org/):

- **Major:** incompatible changes to the documented public contract, including a mod ID
  change, removal of supported environments, or an increased required runtime baseline.
- **Minor:** compatible functionality, deprecations, or substantial internal improvements.
- **Patch:** compatible fixes.

`mod_version` is the single release version, such as `1.3.0`. Artifact versions add build
metadata: `1.3.0+legacy-fabric` and `1.3.0+ornithe`. These have equal SemVer precedence.
The full artifact version is identical in the filename, mod metadata, and Modrinth record.
The Git tag is `v1.3.0`. A prerelease uses, for example, `1.3.0-rc.1+ornithe`.

Build metadata identifies the platform; it is not a substitute for a version increment
when fixing a published binary. Published tags and artifacts are immutable. Internal
Java packages and build task implementation details are not public extension APIs.
