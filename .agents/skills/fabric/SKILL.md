---
name: fabric
description: Resolve Fabric Loader, Fabric API, Loom, Stonecutter, client lifecycle, and networking questions using version-matched documentation and source. Use when answering Fabric questions or changing Fabric or build integration.
---

# Fabric

Read [repository cache guidance](../references/repository-cache.md) before upstream
source lookup. Reuse shallow clones for Fabric API, Loader, Fabric Loom, and
Mixin as needed; search them locally instead of fetching repository pages.

## Resolve the question

1. Read `gradle.properties`, `versions/<mc>/gradle.properties`, `build.gradle.kts`,
   `settings.gradle.kts`, `stonecutter.gradle.kts` and `src/main/resources/fabric.mod.json`
   to identify Minecraft, Loader, Fabric API, the Loom plugin, mappings, entrypoints
   and the client-only environment. Minecraft-specific versions live under `versions/`.
2. Search local imports and registrations for the symbol or behavior in question.
   This project uses Fabric Loom and Stonecutter; a question about a build behavior is
   answered from those plugins' source and documentation, not from Gradle conventions alone.
3. Consult the matching documentation below. For exact signatures or callback
   semantics, inspect the resolved source JAR in the Gradle cache or the matching
   upstream revision. Select a cache artifact by resolved coordinate and version.
4. Answer with the applicable version, supporting links or source paths, and any
   mapping translation. For code changes, compile with the project wrapper and
   report any remaining client validation (`./gradlew :<mc>:runClient`).

## Sources

- [Fabric documentation](https://docs.fabricmc.net/): select the repository's
  Minecraft version; [1.21.1 documentation](https://docs.fabricmc.net/1.21.1/)
  matches the initial project baseline.
- [Fabric developer version selector](https://fabricmc.net/develop/): compatibility
  lookup when evaluating a requested upgrade or a new Minecraft version.
- [Fabric API source](https://github.com/FabricMC/fabric-api): event, networking,
  registry, and module implementations; select the matching release or revision.
- [Fabric Loader source](https://github.com/FabricMC/fabric-loader): entrypoints,
  dependency resolution, jar-in-jar handling and environment behavior.
- [Fabric Loom source](https://github.com/FabricMC/fabric-loom): the build plugin
  used here, including `include`, run configurations and remapping.
- [Stonecutter documentation](https://stonecutter.kikugie.dev/): version-specific
  source preprocessing (`//? if >=1.21.5 {` comments) and multi-version projects.

## Integration checks

- Keep the mod client-only: `fabric.mod.json` declares `"environment": "client"`, and
  classes under `client/` may use `net.minecraft.client`. Nothing may load on a server.
- Fabric Language Kotlin is not a dependency. Kotlin and the other libraries ship as
  jar-in-jar through the `bundled` configuration; confirm a new dependency resolves
  into the `include` loop in `build.gradle.kts` and does not clash with Minecraft's
  own libraries (slf4j, annotations).
- Loom versions track the JDK: Loom 1.18 needs JDK 25, so the build pins 1.17.x for
  JDK 21. Check `docs/known-issues.md` before upgrading it.
- For a lifecycle hook, use Fabric API's `ClientLifecycleEvents` and
  `ClientTickEvents`, and confirm the thread each callback runs on.
- Use the [Mixin skill](../mixin/SKILL.md) for injection semantics, selectors,
  remapping, MixinExtras, and application failures, and the Minecraft skill for
  vanilla client behavior.
