# Changelog

Versions follow [Semantic Versioning 2.0.0](https://semver.org/). The public compatibility
surface is defined in [docs/compatibility.md](docs/compatibility.md).

## [1.2.0] - 2026-11-15

- Build Legacy Fabric and Ornithe artifacts from one four-project Gradle build.
- Provision Java toolchains for Gradle and IntelliJ IDEA builds.
- Share dependency-free visibility logic, translations, and barrier assets.
- Enforce client-thread mutation and publish visibility safely to rendering workers.
- Ignore queued toggles outside a world while retaining visibility across connections.
- Declare client-only initialization and explicit runtime dependencies for both artifacts.
- Verify core behavior, release contracts, Java 8 bytecode, metadata, and packaged resources.
- Publish platform-specific SemVer build identifiers through a shared GitHub release pipeline.

## [1.1.0]

- Add the Ornithe port for Minecraft 1.8.9 using Feather mappings and OSL.

## [1.0.1]

- Declare the Legacy Fabric API dependency.
- Update the CI artifact name.

## [1.0.0]

- Toggle barrier block visibility with a rebindable `B` key.
- Provide translated controls and visibility feedback.

[1.2.0]: https://github.com/metaconscious/legacy-visible-barrier/releases/tag/v1.2.0
[1.1.0]: https://github.com/metaconscious/legacy-visible-barrier/releases/tag/v1.1.0
[1.0.1]: https://github.com/metaconscious/legacy-visible-barrier/releases/tag/v1.0.1
[1.0.0]: https://github.com/metaconscious/legacy-visible-barrier/releases/tag/v1.0.0
