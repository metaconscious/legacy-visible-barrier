# Releasing

## Version and artifacts

Set `mod_version` in `gradle.properties` and add the matching `CHANGELOG.md` entry. The
release version has no build metadata. All three distributions are released together;
their target configuration is in `gradle/targets.json`:

| Release | Artifact and Modrinth version | Modrinth loader | Required dependency project |
| --- | --- | --- | --- |
| `1.3.0` | `1.3.0+legacy-fabric` | `legacy-fabric` | `9CJED7xi` — Legacy Fabric API |
| `1.3.0` | `1.3.0+ornithe` | `ornithe` | `EHGtwpJv`, version `wbCVLcP4` — OSL 0.16.3 Gen1 |
| `1.3.0` | `1.3.0+ornithe.gen2` | `ornithe` | `EHGtwpJv`, version `PSPwlyvh` — OSL 0.17.1 Gen2 |

The Gen1 Modrinth project is `FSLuAotK` (`legacy-visible-barrier`). Gen2 uses a separate
project, configured through `MODRINTH_GEN2_PROJECT_ID`. Modrinth's
[update filters](https://docs.modrinth.com/api/operations/getlatestversionsfromhashes/)
do not distinguish Calamus generations; separate projects prevent cross-generation
update selection. Required OSL version IDs also identify the matching generation.
Each record lists Minecraft
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
creation permission and upload authority for both destination projects. Create the
separate Gen2 project and set its ID in the repository variable `MODRINTH_GEN2_PROJECT_ID`
before releasing. It must differ from `FSLuAotK`.

Use GitHub CLI for repository administration and release operations:

```sh
gh secret set MODRINTH_TOKEN --repo metaconscious/legacy-visible-barrier --env release
gh variable set MODRINTH_GEN2_PROJECT_ID --repo metaconscious/legacy-visible-barrier --body GEN2_PROJECT_ID
```

The publishing job uses GitHub's workflow token through `GH_TOKEN`; it alone receives
repository write permission and the Modrinth token. Build and pull-request jobs receive
read-only repository access and no publishing token.

Staging reads the Gen2 project ID from that environment variable and records it in
`release.json`. Ordinary CI may stage with an unset destination; publication rejects
such a manifest before any upload or GitHub release creation. Set the variable and
rerun verification to produce a publishable manifest. The publisher uses the staged
destination, preserving it across retries.

## Release procedure

1. Merge the reviewed version, changelog, and code changes into `main`.
2. From the clean release checkout, run the full
   [verification sequence](../CONTRIBUTING.md#changes-and-tests), then
   `MODRINTH_GEN2_PROJECT_ID=GEN2_PROJECT_ID ./gradlew stageRelease`. Complete the
   [in-game smoke test](../CONTRIBUTING.md#in-game-smoke-test) for all three artifacts.
3. Create the tag at the reviewed commit and dispatch the release workflow:

   ```sh
   release_tag=v1.3.0
   release_commit=$(git rev-parse HEAD)
   gh api --method POST repos/metaconscious/legacy-visible-barrier/git/refs \
     -f ref="refs/tags/$release_tag" -f sha="$release_commit"
   gh workflow run release.yml --repo metaconscious/legacy-visible-barrier -f tag="$release_tag"
   ```

The workflow also accepts pushed `v*` tags. It requires exact tag/version equality and
checks that the tagged commit belongs to `main`. The reusable verification workflow builds
all distributions, runs unit tests, twelve production-client profiles and two
cross-generation rejection checks, verifies their contents, and stages their hashes and
source commit. The publisher downloads those same-run artifacts and does not rebuild.

The publisher preflights all three Modrinth records and existing GitHub assets before uploading.
It creates a draft GitHub release, uploads the verified files, publishes the three Modrinth
records, verifies them, and then publishes the GitHub release. The GitHub release contains
all three JARs, `SHA512SUMS`, and `release.json`.

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
