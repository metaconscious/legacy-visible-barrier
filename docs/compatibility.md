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

## Rendering mods and overlays

The following 1.8.9 projects and builds were checked on 2026-11-14.

Modrinth's exact-build download counts were 5,421 / 7,018 for the linked Legacy Fabric /
Ornithe OptiFabric builds, 8,048 for AxolotlClient and 4,479 for Phosphor-Legacy;
these count downloads, not unique Minecraft 1.8.9 users.

| Project | Relevant build and requirements |
| --- | --- |
| Legacy OptiFabric | Separate generation 1 builds: [2.3.6+1.3.1-1.13.2 for Legacy Fabric](https://modrinth.com/mod/legacy-optifabric/version/VxbRKwpM) and [2.3.6+b1.7.3-1.14.4 for Ornithe](https://modrinth.com/mod/legacy-optifabric/version/keYvdDsA). Both use Java 8 and require Loader 0.15.4 or later. Install a separate [OptiFine 1.8.9 jar](https://optifine.net/downloads), such as HD U M5. OptiFabric [replaces game classes](https://github.com/tildejustin/optifabric#how-it-works) with OptiFine's implementations; shader packs and rendering options need separate checks. |
| LegacyEnhance | [1.0.0](https://github.com/Hankung7183/LegacyEnhance/releases/tag/v1.0.0) targets Legacy Fabric 1.8.9, Java 8 and Loader 0.14.19 or later; its bundled configuration library requires Legacy Fabric API 1.7.0 or later. Its [features](https://github.com/Hankung7183/LegacyEnhance#features) include entity/particle culling and lighting/rendering changes. |
| Phosphor-Legacy / FPS Plus | [Phosphor-Legacy 0.1.5+1.8.9](https://modrinth.com/mod/phosphor-legacy/version/UdBfSdpd) optimizes lighting; [FPS Plus Ver-D](https://modrinth.com/mod/fpspluslf/version/Zh2JLFek) changes math functions. These are Java 8 Legacy Fabric builds that retain the terrain renderer. |
| HUD and world overlays | [AxolotlClient 3.0.6+1.8.9](https://modrinth.com/mod/axolotlclient/version/EpBQmzkD), [Extended Ingame Hud 0.1.0](https://modrinth.com/mod/extended-ingame-hud/version/VksKaGko), [Legacy Block Highlight 1.0.0](https://modrinth.com/mod/legacy-block-highlight/version/1CYsBYnm) and [Legacy Hitbox 1.0.0](https://modrinth.com/mod/legacy-hitbox/version/JC9A2uTa) have Java 8 Legacy Fabric builds whose declared dependencies fit the baseline above. |
| Oldium | The [1.8.9 release](https://github.com/OldiumMC/oldium/releases/tag/1.8.9), `oldium-fabric-1.8.9-1.0-SNAPSHOT.jar`, is a Legacy Fabric terrain-renderer replacement requiring **Java 17 or later**. |
| Carbon | [Source is available](https://github.com/ChlorineMC/Carbon-Mod), targeting Legacy Fabric 1.8.9 and [Java 17 or later](https://github.com/ChlorineMC/Carbon-Mod/blob/master/src/main/resources/fabric.mod.json). The repository has no published GitHub release; its former Modrinth `carbon-performance` page is unavailable. |
| Argentum / Argentum Extras | [Argentum 1.0.2](https://modrinth.com/mod/argentum/version/RxzOYPTx) uses the Celeritas terrain renderer. Its [release build configuration](https://github.com/QuicksilverMC/Argentum/blob/v1.0.2/buildSrc/src/main/kotlin/mc.gradle.kts) uses **Ornithe generation 2 and Java 25**. It and its Extras HUD/visual controls are outside this release's generation 1 support matrix. |
| Furiah's Visuals | The author's [installation requirements](https://furiah.net/download?loader=fabric) specify Legacy Fabric, Java 8, Loader 0.16 or later and **Legacy Fabric API 1.13.5 or later**. This needs an API upgrade from the baseline above. Its shader effects have their own OptiFine/shader-pack fallback rules. |

The official [MiniHUD 1.8.9 files](https://modrinth.com/mod/minihud/versions?g=1.8.9)
target Forge. The official [Tweakeroo](https://modrinth.com/mod/tweakeroo/versions)
and [Litematica](https://modrinth.com/mod/litematica/versions) listings contain no
1.8.9 builds.

## Barrier render-pass compatibility

The barrier pass runs in `GameRenderer` immediately after the translucent terrain call.
It remains reachable when a mod overwrites or cancels the terrain-layer method; a
renderer that removes that call from `GameRenderer` needs a separate integration.
The hook does not replace game methods or capture local variables.

Drawing preserves client vertex arrays, generic shader attributes, VBO bindings,
current attributes and Minecraft's cached color, lightmap state and texture matrix,
active texture units, and matrix mode. It inherits the caller's blend, depth, fog,
shader program, framebuffer, and camera matrices. The pass does not clear buffers or
invalidate terrain. Chunk indexes belong to client chunks; packet updates and reads
run on the client thread. Missing chunks contribute no barriers, and no server chunk
loading or background world reads are requested.

Barrier quads are sorted within their own batch, after translucent terrain. They are
not globally sorted with water, stained glass, or another mod's translucent geometry;
intersections can blend in the wrong order. Opaque blocks still occlude barriers.
Barriers are absent from terrain meshes, including OptiFine's terrain shadow pass.
External shader packs and their material, shadow, or post-processing effects have not
been validated. OptiFine's internal shader mode is a separate tested case below.

### Tested combinations for 1.3.0

Tests use remapped production JARs on Linux with Java 8, except where indicated.
The client suite covers barrier-only pixels and model submissions, opaque occlusion,
adjacent faces, repeated toggles without terrain invalidation, both terrain backends,
chunk/world/resource changes, and restoration of seeded foreign graphics state.
A separate fixture cancels terrain drawing to check that the frame hook still runs.

| Combination | Result |
| --- | --- |
| Legacy Fabric or Ornithe + the OptiFabric 2.3.6 builds above + OptiFine HD U M5 | Passed with shaders off, Fast Render off/on, and separately with the internal shader mode and Fast Render off. No external shader pack was installed. |
| Legacy Fabric + LegacyEnhance 1.0.0, Phosphor-Legacy 0.1.5+1.8.9, FPS Plus Ver-D, Legacy Hitbox 1.0.0, Legacy Block Highlight 1.0.0, Extended Ingame Hud 0.1.0 | Passed together using default mod configuration. |
| Legacy Fabric + AxolotlClient 3.0.6+1.8.9 | Passed using default mod configuration. |
| Legacy Fabric + published Oldium 1.8.9 JAR, Java 21 | Startup failed before world loading: the JAR bundles `net.fabricmc.api.EnvType`, causing a loader constraint violation. The same Java 21 profile passed without Oldium. |

Coverage is LVB's rendering and lifecycle under the listed settings. Carbon, Argentum,
and Furiah's Visuals are not tested combinations for this release.

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
