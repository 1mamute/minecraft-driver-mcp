---
name: mixin
description: Resolve SpongePowered Mixin and MixinExtras questions using version-matched source and documentation. Use when researching or changing injection points, target descriptors, remapping, local capture, accessors, or mixin application failures.
---

# Mixin and MixinExtras

Read [repository cache guidance](../references/repository-cache.md) before upstream
source lookup. Reuse resolved source JARs and shallow clones of Mixin,
Fabric Loader, and MixinExtras as needed; search their source locally.

## Resolve the question

1. Read `gradle.properties`, `build.gradle.kts`, `src/main/resources/fabric.mod.json`,
   and `src/main/resources/minecraft-driver-mcp.mixins.json`. Inspect the relevant Java
   mixins under `src/main/java/io/github/ummamute/driver/mixin/`.
2. Establish the resolved Mixin implementation, artifact version, and MixinExtras
   version from Gradle dependency resolution, Loader metadata, or runtime logs.
   Check whether Loader supplies a Fabric fork of Mixin. Match source to that
   artifact's upstream revision and patches; a Loader version or config
   `minVersion` alone does not identify the running implementation.
3. Locate the exact target class and method in matching mapped Minecraft sources
   or the installed mod's artifact/source. Use the Minecraft skill for target
   behavior, and the Fabric skill for Loader/Loom integration.
   Record the method descriptor and namespace, including types in its signature.
4. Consult the sources below for the annotation or failure in question. Confirm
   selectors, handler signatures, and injection semantics in the resolved
   implementation. When source leaves the bytecode shape uncertain, inspect the
   matching class with `javap -p -c -s` or the available decompiler; account for
   Kotlin bridges, synthetic methods, and generated overloads.
5. Answer with the applicable versions, mapping namespace, and supporting local
   paths or commit-specific source links. Separate verified target behavior from
   inference and identify missing source or runtime evidence. For changes, run
   the JDK 21 wrapper build and a client smoke check (`./gradlew :<mc>:runClient`) that exercises the
   target; report runtime validation that was not performed.

## Sources

- [SpongePowered Mixin source](https://github.com/SpongePowered/Mixin): annotation
  contracts under `src/main/java/org/spongepowered/asm/mixin/`, injection
  implementation under `injection/`, and transformation under `transformer/`.
  Search the matching revision for the annotation, injection point, or exception.
- [Mixin documentation](https://github.com/SpongePowered/Mixin/wiki): conceptual
  guidance and injection tutorials. Wiki examples may use another release or
  mapping namespace; confirm exact behavior against resolved source.
- [Fabric's Mixin fork](https://github.com/FabricMC/Mixin): consult when resolved
  artifact metadata identifies the fork; compare with SpongePowered only when
  the distinction affects the question.
- [Fabric Loader source](https://github.com/FabricMC/fabric-loader): Mixin
  dependency selection, bootstrap, and environment integration.
- [MixinExtras source and documentation](https://github.com/LlamaLad7/MixinExtras):
  use for `com.llamalad7.mixinextras` annotations such as `@Local`, when a
  mixin uses them. Inspect the matching release's implementation and consult
  its [wiki](https://github.com/LlamaLad7/MixinExtras/wiki) for the relevant feature.
- [Fabric Loom source](https://github.com/FabricMC/fabric-loom):
  use for this build's annotation processing, refmap generation, and remapping.

## Target and application checks

- Trace `fabric.mod.json` registration through the mixin config to the class.
  Check environment, config/plugin conditions, compatibility level, and required
  injection counts before treating an unapplied mixin as a selector problem.
- Resolve the target method and each `@At` member reference independently.
  Confirm owner, name, descriptor, overload, opcode, slice, ordinal, and shift
  against the actual method body. Establish how many instructions should match
  and why that location preserves the intended behavior.
- Decide remapping separately for each selector from its namespace and the
  annotation's supported controls. Another mod's class can have mapped Minecraft
  types in its descriptor; verify the generated refmap and remapped artifact
  rather than inferring `remap = false` from the owner's package.
- Match static/instance context, callback return type, cancellation behavior,
  and captured arguments or locals to the target at the selected instruction.
  For `@Local`, confirm the installed MixinExtras contract and local bytecode
  types; decompiled variable names alone are insufficient evidence.
- When choosing an injector, compare its effect and interaction with other
  mixins. Check the installed version before proposing MixinExtras alternatives.
  For accessors, invokers, shadows, or overwrites, verify the exact member and
  transformation constraints in matching source.
- For application failures, connect the complete exception chain to the target,
  selector, and runtime artifact. Use version-supported Mixin debug/export
  options when transformed bytecode is needed, and inspect other transformations
  that affect the same method. Compilation alone does not prove application in
  a remapped client runtime.
