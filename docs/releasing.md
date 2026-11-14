# Releasing

## Version and artifacts

Set `mod_version` in `gradle.properties` and add the matching `CHANGELOG.md` entry. The
release version has no build metadata. Both platforms are released together:

| Release | Artifact and Modrinth version | Modrinth loader | Required dependency project |
| --- | --- | --- | --- |
| `1.2.0` | `1.2.0+legacy-fabric` | `legacy-fabric` | `9CJED7xi` — Legacy Fabric API |
| `1.2.0` | `1.2.0+ornithe` | `ornithe` | `EHGtwpJv` — Ornithe Standard Libraries |

The Modrinth project is `FSLuAotK` (`legacy-visible-barrier`). Each record lists Minecraft
`1.8.9` and one primary remapped JAR. Modrinth permits duplicate version numbers, but its
[number lookup returns the oldest matching record](https://docs.modrinth.com/api/operations/getversionfromidornumber/).
Distinct build metadata gives the artifacts distinct full version strings. Publication
still verifies loader, game version, dependencies, filename, and hash before reusing a
record, and retains the returned version IDs.

Stable versions use Modrinth's `release` channel. `alpha` prereleases use `alpha`; other
prereleases, including release candidates, use `beta`. GitHub prerelease status follows
the presence of a SemVer prerelease identifier.

## Repository setup

Maintain `main` as the default branch and require the CI verification job before merging.
Protect release tags from modification and deletion. Configure a GitHub Actions
environment named `release` and its `MODRINTH_TOKEN` secret. The token needs version
creation permission and upload authority for `FSLuAotK`.

Use GitHub CLI for repository administration and release operations:

```sh
gh secret set MODRINTH_TOKEN --repo metaconscious/legacy-visible-barrier --env release
```

The publishing job uses GitHub's workflow token through `GH_TOKEN`; it alone receives
repository write permission and the Modrinth token. Build and pull-request jobs receive
read-only repository access and no publishing token.

## Release procedure

1. Merge the reviewed version, changelog, and code changes into `main`.
2. From the clean release checkout, run `./gradlew clean build smokeTest stageRelease` and complete
   the [in-game smoke test](../CONTRIBUTING.md#in-game-smoke-test) for both artifacts.
3. Create the tag at the reviewed commit and dispatch the release workflow:

   ```sh
   release_tag=v1.2.0
   release_commit=$(git rev-parse HEAD)
   gh api --method POST repos/metaconscious/legacy-visible-barrier/git/refs \
     -f ref="refs/tags/$release_tag" -f sha="$release_commit"
   gh workflow run release.yml --repo metaconscious/legacy-visible-barrier -f tag="$release_tag"
   ```

The workflow also accepts pushed `v*` tags. It requires exact tag/version equality and
checks that the tagged commit belongs to `main`. The reusable verification workflow builds
both distributions once, runs unit and client smoke tests, verifies their contents, and stages their hashes and
source commit. The publisher downloads those same-run artifacts and does not rebuild.

The publisher preflights both Modrinth records and existing GitHub assets before uploading.
It creates a draft GitHub release, uploads the verified files, publishes the two Modrinth
records, verifies them, and then publishes the GitHub release. The GitHub release contains
both JARs, `SHA512SUMS`, and `release.json`.

## Recovery

Publication across services is not atomic. If an upload fails, inspect the failed job and
retry it with `gh run rerun RUN_ID --failed --repo metaconscious/legacy-visible-barrier`.
If retained workflow artifacts have expired, dispatch `release.yml` again with the same
existing tag. Identical artifacts are reused; only absent records or assets are uploaded.
An uncertain Modrinth response is reconciled by reading the existing records before any
further upload. Conflicting hashes, metadata, or duplicate records fail publication.

Per-tag concurrency prevents simultaneous publication by this workflow. Publishing runs
are never cancelled by a newer run. Do not replace published binaries or move tags to
repair a release; prepare a new SemVer release when the binary needs correction.
