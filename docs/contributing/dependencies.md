# Dependencies and versions

Where each version lives, what constrains it, and how to move it forward.

| Version | Where | Constraint |
| --- | --- | --- |
| Minecraft, Fabric API, Parchment | `versions/<mc>/gradle.properties` | One set per supported Minecraft version. Fabric API and Parchment must have a release for that version. |
| Fabric Loader | `gradle.properties` | Shared by every Minecraft version. |
| Fabric Loom | `gradle.properties` (`loom_version`) | Loom 1.18 needs JDK 25. The build uses JDK 21, so it stays on 1.17.x. |
| Kotlin | `gradle.properties` | Used for the compiler and the bundled stdlib. |
| MCP Kotlin SDK, Ktor | `gradle.properties` | Bundled in the jar. The SDK is pre-1.0 and its API can change between minor versions. |
| Stonecutter | `settings.gradle.kts` | Pin it; read the changelog before upgrading. |
| ktlint, detekt, JUnit | `gradle.properties` | Build only. |
| Java | `versions/<mc>/gradle.properties` (`java_version`) | Follows the Minecraft version. |

Choose stable releases; leave out snapshots, betas and release candidates. When a bundled library is added or its license
changes, update the license table in [the introduction](../introduction.md#license).

## Version sources

- [Fabric developer version selector](https://fabricmc.net/develop/) for Loader, Fabric API
  and Yarn/Loom compatibility per Minecraft version
- [Fabric API](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml),
  [Fabric Loader](https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml)
- [Parchment](https://parchmentmc.org/docs/getting-started) for the mappings version per
  Minecraft version
- [Gradle releases](https://services.gradle.org/versions/current)
- [Maven Central](https://repo.maven.apache.org/maven2/) for Kotlin, Ktor and the MCP SDK
  (`io.modelcontextprotocol:kotlin-sdk-server`)

## Adding a Minecraft version

1. Add `versions/<mc>/gradle.properties` with `minecraft_version`, `fabric_version`,
   `parchment_version` and `java_version`.
2. Add the version to `versions(...)` in `settings.gradle.kts`.
3. Build it. The compiler reports what changed in `client/`; fix each difference with a
   Stonecutter conditional, or a per-version class when it grows beyond a few lines.
4. Run the [runtime check](testing.md#runtime-check) on that version and add it to the
   supported list in `README.md`.

Record how much work the new version took in [known-issues.md](known-issues.md); that is
the measure of whether one source tree stays cheaper than separate branches.

## Upgrading a dependency

1. Change the version in the file above.
2. `./gradlew build` for compilation, unit tests and lint.
3. Run the [runtime check](testing.md#runtime-check): the jar-in-jar packaging and the
   bundled libraries only show problems when the client starts.
4. If the jar size changes a lot, check what the new version pulled in transitively.
