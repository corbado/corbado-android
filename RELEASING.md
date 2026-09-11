# Releasing

`com.corbado:connect-core` (module `:sdk`) is published to Maven Central by GitHub Actions.
The version lives in `sdk/gradle.properties` (`VERSION_NAME`) and is released by pushing a
matching `v<semver>` tag.

| Channel | Trigger | Result |
|---|---|---|
| CI | pull requests, push to `main` | build library + example app (`ci.yml`) |
| Production | push tag `v<semver>` | `com.corbado:connect-core:X.Y.Z` to Maven Central (`release.yml`) |

## Releasing `com.corbado:connect-core`

1. Bump `VERSION_NAME` in `sdk/gradle.properties` and add a `CHANGELOG.md` entry; commit
   (`vX.Y.Z`) and merge to `main`.
2. Tag and push to the private repo (the workflow only runs there; `corbado/corbado-android`
   is a mirror that receives `main` and the tag afterwards):
   ```bash
   git tag vX.Y.Z
   git push private main vX.Y.Z
   git push public main vX.Y.Z
   ```
   The workflow checks that the tag matches `VERSION_NAME`, builds and tests, publishes to
   Maven Central and releases the deployment automatically.
3. Create a GitHub release from the tag with the changelog notes.

`:api` (`com.corbado:connect-api`) is a dependency of `:sdk` and is not part of this
workflow. It is versioned independently in `api/build.gradle.kts` and only needs to be
published (manually, `./gradlew :api:publishAndReleaseToMavenCentral`) when its version changes.

## Required GitHub secrets (environment `maven-central`)

| Secret | Content |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal token username (central.sonatype.com → Account → Generate User Token) |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal token password |
| `SIGNING_KEY` | ASCII-armored private GPG key: `gpg --export-secret-keys --armor <KEY_ID>` |
| `SIGNING_KEY_PASSWORD` | Passphrase of that key |

The same token/key pair as the `corbado/android` repo can be reused (same `com.corbado`
namespace, different artifact ids).

## Local publishing (fallback)

```bash
./gradlew :sdk:publishAndReleaseToMavenCentral \
  -PVERSION_NAME=X.Y.Z \
  -PmavenCentralUsername=... -PmavenCentralPassword=... \
  -PsigningInMemoryKey="$(gpg --export-secret-keys --armor <KEY_ID>)" \
  -PsigningInMemoryKeyPassword=...
```
