# Compatibility

## Supported environments

All four artifacts target the Minecraft **1.8.9 client**, using Java 8 bytecode. Java 8 is
the runtime baseline. Building uses JDK 21 and runs the core tests on JDK 8.

| Artifact | Mapping environment | Loader used to build | Required API |
| --- | --- | --- | --- |
| `+legacy-fabric` | Legacy Fabric intermediary generation 1; Yarn build 568 | Fabric Loader 0.16.13 | Legacy Fabric API 1.11.1+1.8.9 or compatible later version |
| `+ornithe` | Calamus generation 1; Feather build 28 | Fabric Loader 0.18.1 | OSL 0.16.3, including keybinds 0.1.3 and lifecycle-events 0.5.6 |
| `+ornithe.gen2` | Calamus generation 2; Feather Gen2 build 2 | Fabric Loader 0.18.1 | OSL Gen2 0.17.1 or compatible later Gen2 version |
| `+forge` | SRG; MCP stable 22 development names | Forge 11.15.1.2318 | None; Mixin 0.8.7 is embedded |

Legacy Fabric metadata retains its existing loader minimum of 0.14.19. Ornithe metadata
requires loader 0.18.1. Gen1 declares OSL core 0.6.0, entrypoints 0.4.3, keybinds 0.1.3,
lifecycle-events 0.5.6 and resource-loader 0.5.4. Gen2 declares core 0.7.0, entrypoints
0.5.0, keybinds 0.2.0, lifecycle-events 0.6.0 and resource-loader 0.6.0.
Install the API distribution matching the selected mapping environment. Legacy Fabric's
own intermediary generation 2 is outside this release's support matrix.

Use an Ornithe Gen2 launcher profile for `+ornithe.gen2`; the
[official profile](https://meta.ornithemc.net/v3/versions/gen2/fabric-loader/1.8.9/0.18.1/profile/json)
enables Fabric's package-access fix. LVB rejects a mismatched mapping generation before enabling
rendering hooks. Native mode cannot make a JAR built for another generation compatible.

Ornithe Gen1 and Gen2 also run on Quilt Loader **0.30.2**, using the same LVB artifact
and matching OSL distribution. Use the official Ornithe Quilt
[Gen1 profile](https://meta.ornithemc.net/v3/versions/gen1/quilt-loader/1.8.9/0.30.2/profile/json)
or [Gen2 profile](https://meta.ornithemc.net/v3/versions/gen2/quilt-loader/1.8.9/0.30.2/profile/json).
Gen2 needs `-Dloader.fixPackageAccess=true`; both profiles identify Minecraft with
`-Dloader.gameVersion=1.8.9`. Quilt's Fabric compatibility layer supplies the existing
entrypoints and loader API. No Quilt-specific LVB JAR or QSL dependency is needed.

Forge uses its own `+forge` distribution, without Fabric Loader, OSL or OptiFabric.
Its LaunchWrapper bootstrap includes the Java 8 runtime of upstream Mixin 0.8.7.
Forge's original ASM and Guava libraries are retained. Install LVB as a client mod;
Forge's built-in **Mods → Legacy Visible Barrier → Config** opens its settings screen.

The mod is client-only. It does not change collision, server state, or world data. All
artifacts have the same mod ID and are alternative installations.

## Rendering compatibility

In enhanced mode, visible barriers share a far-to-near draw order with the current translucent chunk
vertices, including water and glass. Existing terrain geometry is reused and submitted
once. Toggling does not rebuild chunks or tessellate other block models. Opaque blocks
occlude barriers. Sorting uses Minecraft's quad-centre rule; intersecting custom
geometry retains the limitations of that rule.

The pass preserves vertex-array, atlas, lightmap and colour state. It uses Minecraft's
vertex uploader, retaining OptiFine's shader attributes and multi-texture handling.
OptiFine draws barriers in the main translucent shader stage; barriers do not participate
in its terrain shadow pass.

A renderer replacing `GameRenderer` must retain its translucent terrain invocation.
Shared ordering also requires the native compiled-chunk submission path in
`WorldRenderer`. Cancelling that layer still permits the caller to draw barriers, but
does not expose replacement terrain geometry for shared ordering. The exact hooks and
ownership contracts are in [architecture.md](architecture.md).

### Rendering modes

Use the settings screen or edit `config/lvb.properties` in the Minecraft instance to
select a mode. Changes take effect after restarting the client. The defaults are:

```properties
renderMode=auto
nativeRefresh=dirty
```

| `renderMode` | Behavior |
| --- | --- |
| `auto` | Start with enhanced rendering; switch to native rendering when an essential optional hook is absent or an enhanced operation fails with successful state cleanup. |
| `enhanced` | Explicitly request enhanced rendering, with the same recovery policy as `auto`. |
| `native` | Use native translucent terrain meshes. Advanced LVB frame, chunk-index, vertex-origin and compiled-mesh Mixins are omitted at startup. |
| `off` | Disable LVB visualization hooks; B reports that visualization is disabled. |

Enhanced mode preserves shared barrier/water/glass ordering and performs no
toggle-triggered terrain rebuilds. Native mode adds visible barriers to normal terrain
meshes. Its transparency and shader behavior follow the installed terrain renderer.

`nativeRefresh=dirty` marks the finite current view dirty, retains existing meshes, and
lets Minecraft replace them. Visibility converges section by section; terrain models
are rebuilt. Multiple key presses are coalesced into at most one refresh per tick.
`nativeRefresh=reload` uses the original full renderer reload, which can cause terrain
flicker. Use it when a replacement renderer does not honor ordinary region updates.
Neither refresh policy loads world chunks or changes world data.

A recovered enhanced failure switches to native mode for the remainder of the process,
preserving visibility intent. LVB finishes graphics-state cleanup before handing over,
reports the change once, and does not retry the failed renderer each frame. A missing
baked barrier model or a model failure caught by the enhanced pass makes visualization
unavailable, retains vanilla barrier particles, and reports unavailability instead of
successful visibility.

If a mod changes an enhanced transformation target in a way that prevents startup,
select `native` before launching again. Mixin transformation failures cannot be undone
by a runtime mode switch. A renderer that silently bypasses an otherwise present hook
also requires explicit native selection. Missing loader dependencies and another mod's
startup failure remain separate installation problems. The known conflicts below apply
independently of LVB's rendering mode.

JVM properties `-Dlvb.renderMode=native` and `-Dlvb.nativeRefresh=reload` override the
corresponding file settings. An unreadable or invalid configuration selects native mode
with dirty refresh and records the configuration error in the game log.

### Mod Menu

The settings screen is available through a matching Mod Menu's configuration button,
or an independently rebindable **Open LVB Settings** control. Mod Menu is optional.
The UI labels `auto` and its existing `enhanced` alias as **Automatic**, `native` as
**Compatibility**, and `off` as **Disabled**. An unrelated edit preserves an existing
`enhanced` file value.

| Distribution | Tested Mod Menu | API/runtime profile |
| --- | --- | --- |
| Legacy Fabric Gen1 | [Legacy Mod Menu 1.1.0](https://modrinth.com/mod/legacy-mod-menu/version/r7PigOJk) | Legacy Fabric API baseline |
| Ornithe Gen1 | [0.3.1+mc1.8.9](https://modrinth.com/mod/modmenu-ornithe/version/YQcg9CXT) | OSL Gen1 baseline |
| Ornithe Gen2 | [0.4.0+mc1.8.9](https://modrinth.com/mod/modmenu-ornithe/version/dqbY1yZh) | OSL Gen2 baseline |
| Ornithe Gen2 | [0.5.0+mc1.8.9](https://modrinth.com/mod/modmenu-ornithe/version/71EHHNXc) | Additional OSL localization 0.1.0, text-components 0.1.0-alpha.3 and executors 0.1.0; core 0.9.0 and resource-loader 0.7.2 |

The Ornithe baseline Mod Menu profiles also run on Quilt 0.30.2.
These UI profiles use Java 8 and the loaders listed above. Exact Minecraft-specific
module coordinates are pinned in `gradle/targets.json`. The 0.5 profile's additional
libraries belong to that test profile; LVB's required API versions remain unchanged.
Legacy Mod Menu 0.1.0 and 1.0.0 have incompatible APIs and mapped screen identities;
Legacy Fabric metadata declares those versions incompatible.

Screen opening, saving and closing leave the active renderer, recovery state and
visibility unchanged. Save applies on the next launch; JVM overrides remain effective.
Cancel and Escape discard the draft. Reset needs an explicit Save. The screen supports
keyboard focus, scrolling and resizing, and delegates rebinding to Minecraft Controls.
Unreadable files disable editing until reloaded. Invalid files use safe values and are
backed up beside `lvb.properties` on an explicit repair save. An external edit or failed
atomic replacement keeps the draft open and reports the error.

### Tested Gen1 mod combinations

The following Gen1 builds were exercised with the enhanced renderer, visible and hidden
barriers, VBO and display-list terrain, resource reloads and world changes. Mod versions and enabled features are
part of each combination.

| Environment | Mods | Settings |
| --- | --- | --- |
| Legacy Fabric Gen1 and Ornithe Gen1 | OptiFine HD U M5 with OptiFabric [2.3.6+1.3.1-1.13.2 for Legacy Fabric](https://modrinth.com/mod/legacy-optifabric/version/VxbRKwpM) or [2.3.6+b1.7.3-1.14.4 for Ornithe](https://modrinth.com/mod/legacy-optifabric/version/keYvdDsA) | Shaders off with Fast Render off/on; internal shaders with Fast Render off. |
| Legacy Fabric Gen1 and Ornithe Gen1 | OptiFine HD U M5 with the matching OptiFabric build | Render Regions enabled; shaders off, Fast Render off, anisotropic filtering 16× (multi-texture terrain). |
| Legacy Fabric Gen1 and Ornithe Gen1 | OptiFine HD U M5, the matching OptiFabric build and [BSL 10.1.8](https://modrinth.com/shader/bsl-shaders/version/Y68yiql9) | External pack active, Render Regions enabled, Fast Render off, anisotropic filtering 1×. |
| Legacy Fabric Gen1 | LegacyEnhance 1.0.0, Phosphor-Legacy 0.1.5+1.8.9, FPS Plus Ver-D, Legacy Hitbox 1.0.0, Legacy Block Highlight 1.0.0 and Extended Ingame Hud 0.1.0 | Default configuration, installed together. |
| Legacy Fabric Gen1 | Legacy Hitbox 1.0.0, Legacy Block Highlight 1.0.0, LegacyEnhance 1.0.0 and Phosphor-Legacy 0.1.5+1.8.9 | Gradient hitboxes and volume highlights; also with OptiFine/OptiFabric or AxolotlClient 3.0.6. |
| Legacy Fabric Gen1 | [Legacy Chunk Borders 1.0.0](https://modrinth.com/mod/legacy-chunk-borders/version/hp3oZxHx) | Grid enabled, alone and with OptiFine/OptiFabric. |
| Legacy Fabric Gen1 | [Legacy OrthoCamera 1.0.1](https://modrinth.com/mod/legacy-orthocamera/version/PtBq0srz) | Orthographic projection, first-person camera and fixed rotation disabled; alone and with OptiFine/OptiFabric. |
| Legacy Fabric Gen1 | [AxolotlClient 3.0.6](https://modrinth.com/mod/axolotlclient/version/EpBQmzkD) | Default configuration and custom block outlines. |
| Ornithe Gen1 | [Kabo Village Marker 1.3.0](https://modrinth.com/mod/kabo-village-marker/version/wsVKa3iS) | Village spheres and golem areas; with OptiFine/OptiFabric during active mining. |
| Ornithe Gen1 | [AxolotlClient 3.1.8](https://modrinth.com/mod/axolotlclient/version/ySfusRPJ), [Waypoints 0.0.3](https://modrinth.com/mod/axolotlclient-waypoints/version/wYncBniX) and [legacy-lwjgl3 1.2.10](https://modrinth.com/mod/moehreag-legacy-lwjgl3/version/QeDzTTIm), Java 21 | Custom outlines and a world waypoint label; also with Kabo Village Marker. |
| Ornithe Gen1 | [OfftiFine 2.1.1](https://modrinth.com/mod/offtifine/version/6yzzDHuh), OptiFine/OptiFabric and legacy-lwjgl3 1.2.10, Java 21 | Default networking restrictions. |

Native mode with dirty refresh was also exercised in the following Gen1 combinations,
including barrier pixels, compiled-mesh updates, VBO and display-list terrain, resource
reloads and world changes:

| Environment | Mods | Enabled features |
| --- | --- | --- |
| Legacy Fabric Gen1 and Ornithe Gen1 | OptiFine HD U M5 and the matching OptiFabric build above | BSL 10.1.8 with Render Regions; also shaders off with Render Regions and 16× anisotropic filtering. |
| Legacy Fabric Gen1 | OptiFine/OptiFabric, LegacyEnhance 1.0.0, Phosphor-Legacy 0.1.5+1.8.9, Legacy Hitbox 1.0.0, Legacy Block Highlight 1.0.0 and Legacy Chunk Borders 1.0.0 | Hitboxes, block highlights and chunk grids. |
| Ornithe Gen1 | AxolotlClient 3.1.8, Waypoints 0.0.3, Kabo Village Marker 1.3.0 and legacy-lwjgl3 1.2.10, Java 21 | Custom outlines, waypoint labels and village markers. |

The baseline checks cover enhanced, native, off and competing-hook recovery on all
four production targets, including contained runtime failure and lifecycle handling.
Development profiles also exercise the explicit full-reload option.

The Gen1 Kabo build requires the Minecraft 1.8.9 variants of OSL networking 0.8.0 and config 0.5.2.
The Gen1 AxolotlClient/Waypoints combination requires OSL networking and legacy-lwjgl3
newer than 1.2.8. These dependencies are additional to LVB's baseline APIs.

### Tested Forge combinations

These profiles use Forge **11.15.1.2318** and Java 8. They exercise the installed Forge
JAR, settings factory, barrier/terrain ordering, graphics-state recovery, resource
reloads and world changes.

| Mods | Modes | Enabled features |
| --- | --- | --- |
| [OptiFine HD U M5](https://optifine.net/downloads) | Enhanced and native | Render Regions, shaders off, Fast Render off, anisotropic filtering 16×; hitboxes and block outlines. |
| OptiFine HD U M5 and [BSL 10.1.8](https://modrinth.com/shader/bsl-shaders/version/Y68yiql9) | Enhanced and native | External shader pack active, Render Regions, Fast Render off, anisotropic filtering 1×. |
| [Essential 1.5.2](https://essential.gg/downloads) | Enhanced | Default configuration and Essential's Mixin provider. |
| [Patcher 1.8.9](https://sk1er.club/mods/patcher), Essential 1.5.2 and OptiFine HD U M5 | Enhanced and native | Default mod settings; shared Mixin bootstrap and terrain rendering. |

Forge's OptiFine installation uses the original OptiFine JAR directly. OptiFabric belongs
to the Fabric-compatible environments. Essential is optional for the ordinary Forge
installation; the packaged Mixin runtime starts LVB independently.

### Forge with LiteLoader

The Forge artifact passed enhanced and native rendering checks with LiteLoader
`1.8.9-20260727.184607-11-release`, Essential **1.5.2** and
[ItemDash 1.0](https://github.com/MineLittlePony/ItemDash/releases/tag/1.0).
ItemDash initialized through LiteLoader while LVB used Forge's lifecycle and settings
integration. This combination uses Essential's `0.2.0+mixin.0.8.7` provider.

The same LiteLoader runtime without the modern provider selects its bundled Mixin
**0.5.3**. LVB's required Mixin configuration rejects that version before Minecraft
starts, including with native rendering selected. Use the tested provider combination
or a Forge-only instance. LVB has no standalone `.litemod` distribution.

### Tested Ornithe Gen2 combinations

These combinations use Fabric Loader 0.18.1 and the complete
[OSL Gen2 0.24.0 distribution](https://modrinth.com/mod/osl/version/TzBDamVN).

| Mods | Runtime and modes | Enabled features |
| --- | --- | --- |
| [Kabo Village Marker 1.4.0](https://modrinth.com/mod/kabo-village-marker/version/JdpzFuM4) | Java 8; enhanced | Village markers across visibility toggles, resource reloads and world changes. |
| [AxolotlClient 3.2.2](https://modrinth.com/mod/axolotlclient/version/GURKlmNL), [Rendering 0.0.12](https://modrinth.com/mod/axolotlclient-rendering/version/q9FM7LrQ), [Waypoints 0.0.4+3.2.0](https://modrinth.com/mod/axolotlclient-waypoints/version/xxFFkQdU), Kabo 1.4.0, [legacy-lwjgl3 1.4.11](https://modrinth.com/mod/moehreag-legacy-lwjgl3/version/bYJVT5uQ) and [MixinExtras 0.5.5](https://repo.maven.apache.org/maven2/io/github/llamalad7/mixinextras-fabric/0.5.5/mixinextras-fabric-0.5.5.jar) | Java 21; enhanced and native dirty refresh | Custom block outlines, waypoint labels and village markers; VBO/display-list terrain, translucent ordering, resource reloads and world changes. |

The Axolotl combination needs the listed MixinExtras update with Loader 0.18.1.
Its bundled MixinExtras 0.5.0 fails during transformation with an annotation-type
`ClassCastException`, including when LVB is absent. Installing 0.5.5 resolves that
startup failure. These Axolotl and LWJGL3 builds require Java 17 or newer; LVB's own
Java 8 baseline is unchanged.

The OptiFabric build tested above targets Calamus Gen1. Its shader results do not apply
to the Gen2 distribution.

### Flap

[Flap](https://wiki.ornithemc.net/wiki/Flap) 0.0.8 passed enhanced and native rendering
checks with the matching `+ornithe.gen2` artifact, Fabric Loader 0.18.1, the OSL Gen2
baseline and Java 8.

Translating the Gen1 LVB artifact into Gen2 with Flap 0.0.8 fails JVM bytecode
verification while Flap rewrites the mapping-resolver calls in LVB's bootstrap.
Install `+ornithe.gen2` directly in Gen2 instances.

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
| Settings control | `key.open_lvb_settings`, initially unbound and rebindable; opens settings in a world |
| Settings persistence | UTF-8 `config/lvb.properties`; explicit Save, restart-only rendering preferences, JVM overrides take precedence |
| Category key | `key.category.legacy_visible_barriers` |
| Feedback keys | `lvb.to_visible`, `lvb.to_invisible`; mode/status keys `lvb.mode_native`, `lvb.unavailable`, `lvb.disabled` |
| Resource namespace | `assets/lvb`, with barrier assets under `assets/minecraft` |
| Translations | German, English, Spanish, French, Japanese, Korean, Russian, Simplified Chinese, Traditional Chinese |
| Initial state | Hidden on client startup |
| State lifetime | Retained across worlds in the same process; not saved to disk |
| Visible state | Translucent barrier model with adjacent faces culled; barrier particles suppressed while visualization is available when the optional particle hook is present |
| Enhanced rendering | Shared translucent ordering; no toggle-triggered terrain reload, chunk rebuild, duplicate terrain submission, or additional non-barrier model tessellation |
| Native rendering | Normal terrain meshes; coalesced dirty-region updates by default, with optional full reload |
| Recovery | Preserve visibility intent; latch native mode after a contained enhanced failure, or report unavailability after a detected shared model failure |
| Input outside a world | Consumed without changing state or sending feedback |
| Metadata | Client environment, explicit game and loader/API requirements; LVB uses CC0-1.0, embedded Forge Mixin uses MIT |

## Semantic versioning

Releases follow [Semantic Versioning 2.0.0](https://semver.org/):

- **Major:** incompatible changes to the documented public contract, including a mod ID
  change, removal of supported environments, or an increased required runtime baseline.
- **Minor:** compatible functionality, deprecations, or substantial internal improvements.
- **Patch:** compatible fixes.

`mod_version` is the single release version, such as `1.5.0`. Artifact versions add build
metadata: `1.5.0+legacy-fabric`, `1.5.0+ornithe`, `1.5.0+ornithe.gen2` and `1.5.0+forge`.
These have equal SemVer precedence.
The full artifact version is identical in the filename, mod metadata, and release manifest.
The Git tag is `v1.5.0`. A prerelease uses, for example, `1.5.0-rc.1+ornithe`.

Build metadata identifies the platform; it is not a substitute for a version increment
when fixing a published binary. Published tags and artifacts are immutable. Internal
Java packages and build task implementation details are not public extension APIs.
