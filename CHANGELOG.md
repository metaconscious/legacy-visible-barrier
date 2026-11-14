# Changelog

Versions follow [Semantic Versioning 2.0.0](https://semver.org/). The public compatibility
surface is defined in [docs/compatibility.md](docs/compatibility.md).

## [1.3.0] - Unreleased

- Eliminate terrain reloads and screen flicker when toggling barrier visibility.
- Draw barriers in a separate, depth-tested translucent pass in both adapters.
- Track barriers through block changes, chunk data updates, unloads, and world changes.
- Preserve lightmap, color, vertex-array and buffer state around barrier drawing,
  including foreign VAOs and element buffers.
- Enforce client-thread index access and non-reentrant rendering; recover the next
  batch after model or upload failures while restoring graphics state.
- Keep the barrier frame hook reachable when a mod replaces or cancels terrain drawing.
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
