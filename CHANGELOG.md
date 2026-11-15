# Changelog

Versions follow [Semantic Versioning 2.0.0](https://semver.org/). The public compatibility
surface is defined in [docs/compatibility.md](docs/compatibility.md).

## [1.3.0] - Unreleased

- Add an Ornithe Calamus Gen2 distribution alongside both existing Gen1 distributions.
- Reject mismatched mapping generations before activating Minecraft rendering hooks.
- Isolate generation-specific sources, dependencies and build outputs;
  verify all three production distributions and cross-generation rejection in CI.
- Remove automated Modrinth publishing; publish all three distributions to GitHub Releases.
- Update the build to Gradle 9.2.1, Loom 1.15.5, Legacy Looming 1.15.3 and Ploceus 1.15.9;
  retain Java 8 mod bytecode and runtime support.
- Eliminate toggle-triggered terrain reloads and screen flicker in enhanced mode.
- Add native compatibility and off modes, selected before Mixin transformation. Native
  mode uses coalesced section updates, with an optional full-reload refresh policy.
- Recover from missing enhanced hooks and contained rendering failures by switching
  to native mode; report shared model failures as visualization unavailable.
- Build barrier geometry separately and share translucent draw ordering with existing
  terrain vertices in both adapters, preserving water and glass blending.
- Track barriers through block changes, chunk data updates, unloads, and world changes.
- Preserve lightmap, color, vertex-array and buffer state around barrier drawing,
  including foreign VAOs and element buffers.
- Enforce client-thread index access and non-reentrant rendering; restore graphics
  state before recovery and keep rendering workers separate from mode decisions.
- Keep the barrier frame hook reachable when a mod replaces or cancels terrain drawing.
- Retain compiled vertex origins, shader attributes and sprite ordering with OptiFine;
  restore atlas bindings after multi-texture uploads.
- Document tested rendering mods, supported loader generations, and translucent ordering.
- Add production-namespace compatibility tests for active hitboxes, block highlights,
  chunk grids, orthographic cameras, village markers, custom outlines and waypoint labels.
- Add client regression checks for rendering isolation, state restoration and lifecycle updates.

## [1.2.0] - Unreleased

- Build Legacy Fabric and Ornithe artifacts from one four-project Gradle build.
- Provision Java toolchains for Gradle and IntelliJ IDEA builds.
- Share dependency-free visibility logic, translations, and barrier assets.
- Enforce client-thread mutation and publish visibility safely to readers.
- Ignore queued toggles outside a world while retaining visibility across connections.
- Declare client-only initialization and explicit runtime dependencies for both artifacts.
- Verify core behavior, release contracts, Java 8 bytecode, metadata, and packaged resources.
- Publish platform-specific SemVer build identifiers through a shared release pipeline.

## [1.1.0]

- Add the Ornithe port for Minecraft 1.8.9 using Feather mappings and OSL.

## [1.0.1]

- Declare the Legacy Fabric API dependency.
- Update the CI artifact name.

## [1.0.0]

- Toggle barrier block visibility with a rebindable `B` key.
- Provide translated controls and visibility feedback.

[1.1.0]: https://github.com/metaconscious/legacy-visible-barrier/releases/tag/v1.1.0
[1.0.1]: https://github.com/metaconscious/legacy-visible-barrier/releases/tag/v1.0.1
[1.0.0]: https://github.com/metaconscious/legacy-visible-barrier/releases/tag/v1.0.0
