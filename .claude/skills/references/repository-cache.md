# Cached dependency repositories

Use local Git checkouts for repository source and repository-hosted documentation.
Reuse resolved source JARs or mapped Minecraft sources when they already answer
the question. Clone only the repositories needed for the current task.

## Cache locations

Store shallow clones under `~/.cache/minecraft-driver-mcp/repos/`. Resolve `~` to the
current user's home directory; in PowerShell use `$HOME` without assigning to it.
These are research checkouts, outside the project and its build.

| Repository | Cache directory | Clone URL |
| --- | --- | --- |
| Fabric API | `fabric-api` | `https://github.com/FabricMC/fabric-api.git` |
| Fabric Loader | `fabric-loader` | `https://github.com/FabricMC/fabric-loader.git` |
| Fabric Loom | `fabric-loom` | `https://github.com/FabricMC/fabric-loom.git` |
| Mixin | `mixin` | `https://github.com/SpongePowered/Mixin.git` |
| Fabric Mixin fork | `fabric-mixin` | `https://github.com/FabricMC/Mixin.git` |
| MixinExtras | `mixinextras` | `https://github.com/LlamaLad7/MixinExtras.git` |
| Brigadier | `brigadier` | `https://github.com/Mojang/brigadier.git` |
| Stonecutter | `stonecutter` | `https://github.com/stonecutter-versioning/stonecutter.git` |
| Kotlin MCP SDK | `kotlin-sdk` | `https://github.com/modelcontextprotocol/kotlin-sdk.git` |
| MCP specification | `mcp-specification` | `https://github.com/modelcontextprotocol/modelcontextprotocol.git` |
| Ktor | `ktor` | `https://github.com/ktorio/ktor.git` |

## Acquire and reuse

The user authorizes these skills to create missing cache directories and shallow
clones here for source lookup. Follow the current environment's filesystem and
network permissions; this authorization does not bypass an enforced sandbox.

1. Resolve the required version/ref from the project (`gradle.properties`). Check
   whether the cache directory exists. For an existing checkout, inspect its
   origin, `git status --short`, and `git rev-parse HEAD`; reuse it rather than
   recloning or refreshing on every question. Preserve local modifications.
2. If absent, create the cache parent and run
   `git clone --depth 1 --single-branch <clone-url> <cache-directory>`.
   Add `--branch <tag-or-branch>` when the matching ref is known. Use a confirmed
   upstream ref rather than assuming a dependency version is also a tag name.
3. If an existing shallow clone lacks the required ref, fetch only that ref:
   `git -C <cache-directory> fetch --depth 1 origin <ref>`.
   Read it with `git show FETCH_HEAD:<path>` or use a clean detached checkout.
   Inspect local changes before switching revisions; never reset or clean them.
4. Search with scoped `rg` queries and read the relevant files or line ranges.
   For another revision, use `git ls-tree` and `git show` without switching the
   shared checkout. Avoid dumping entire repositories or large source files.
5. Record the inspected commit and cite the local path or a commit-specific
   upstream permalink. Construct links from the origin, commit, and file path;
   no remote page fetch is needed just to create a citation.

PowerShell example for a missing clone:

```powershell
$dependencyCache = Join-Path $HOME '.cache/minecraft-driver-mcp/repos'
$sdkCheckout = Join-Path $dependencyCache 'kotlin-sdk'
if (-not (Test-Path -LiteralPath $sdkCheckout)) {
    New-Item -ItemType Directory -Force -Path $dependencyCache | Out-Null
    git clone --depth 1 --single-branch https://github.com/modelcontextprotocol/kotlin-sdk.git $sdkCheckout
}
git -C $sdkCheckout rev-parse HEAD
rg -n 'mcpStatelessStreamableHttp' $sdkCheckout
```

Use the equivalent `mkdir -p` and `git clone` on POSIX shells. Git transport may
use HTTPS; the goal is to avoid repeatedly fetching rendered repository pages or
individual source files over HTTP. Prefer cached source and bundled docs first.
Use external documentation sites only for information unavailable locally or
when the task calls for checking current published guidance. If cloning/fetching
is unavailable, use existing artifacts and state any remaining evidence gap.

Minecraft's vanilla source is obtained through Loom, not a public source clone.
