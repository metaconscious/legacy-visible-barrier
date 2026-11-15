# Releasing

## Version and artifacts

Set `mod_version` in `gradle.properties` and add the matching `CHANGELOG.md` entry. The
release version has no build metadata. All three distributions are published together
in one GitHub release; their target configuration is in `gradle/targets.json`:

| Target | Artifact version |
| --- | --- |
| Legacy Fabric Gen1 | `1.4.0+legacy-fabric` |
| Ornithe Gen1 | `1.4.0+ornithe` |
| Ornithe Gen2 | `1.4.0+ornithe.gen2` |

Each version appears in its JAR filename and mod metadata. GitHub prerelease status
follows the presence of a SemVer prerelease identifier, such as `1.4.0-rc.1`.
The release contains all three remapped JARs, `SHA512SUMS`, and `release.json` with the
source commit, artifact versions, sizes and hashes.

## Repository setup

Maintain `main` as the default branch and require the CI verification job before merging.
Protect release tags from modification and deletion. Configure a GitHub Actions
environment named `release`.

The publishing job uses GitHub's automatic workflow token through `GH_TOKEN` and receives
repository write permission. Build and pull-request jobs receive read-only repository
access. Use GitHub CLI for repository administration and release operations.

## Release procedure

1. Merge the reviewed version, changelog, and code changes into `main`.
2. From the clean release checkout, run the full
   [verification sequence](../CONTRIBUTING.md#changes-and-tests), then
   `./gradlew stageRelease`. Complete the
   [in-game smoke test](../CONTRIBUTING.md#in-game-smoke-test) for all three artifacts.
3. Create the tag at the reviewed commit. This starts the release workflow:

   ```sh
   release_tag=v1.4.0
   release_commit=$(git rev-parse HEAD)
   gh api --method POST repos/metaconscious/legacy-visible-barrier/git/refs \
     -f ref="refs/tags/$release_tag" -f sha="$release_commit"
   ```

The workflow also accepts pushed `v*` tags. It requires exact tag/version equality and
checks that the tagged commit belongs to `main`. The reusable verification workflow builds
all distributions, runs unit and Windows persistence tests, sixteen production-client profiles and two
cross-generation rejection checks, verifies their contents, and stages their hashes and
source commit. The publisher downloads those same-run artifacts and does not rebuild.

The publisher checks the source commit, manifest and file hashes, then creates or resumes
a draft GitHub release. It checks existing release metadata and assets before uploading
missing files, then publishes the completed release.

## Recovery

If an upload fails, inspect the failed job and retry it with
`gh run rerun RUN_ID --failed --repo metaconscious/legacy-visible-barrier`.
If retained workflow artifacts have expired, dispatch the existing tag's workflow:

```sh
gh workflow run release.yml --repo metaconscious/legacy-visible-barrier \
  --ref v1.4.0 -f tag=v1.4.0
```

Identical assets are reused; only absent files are uploaded. Conflicting hashes,
metadata, or duplicate releases fail publication.

Per-tag concurrency prevents simultaneous publication by this workflow. Publishing runs
are never cancelled by a newer run. Do not replace published binaries or move tags to
repair a release; prepare a new SemVer release when the binary needs correction.
