---
name: release
description: Cut a minecraft-driver-mcp release end to end. Covers choosing the version, the release pull request, the tag, the GitHub release, npm publication of the gateway, Maven Central staging and publication, and checking every channel afterwards. Use when asked to release, cut, tag or publish a version.
---

# Release

A release ships through four channels from one `v<mod_version>` tag:

| Channel | Produced by | Contents |
| --- | --- | --- |
| GitHub release | `Release` workflow on the tag push | Notes from `scripts/changelog.mjs`, the mod jar per Minecraft version, the gateway `.tgz` |
| npm | `publish-npm` job of the same run | The gateway archive attached to the GitHub release, with provenance |
| Maven Central | `Stage Maven Central` workflow (manual dispatch), then a manual publish in Central Portal | Signed jar, sources, Javadoc, POM and Gradle module per Minecraft version |
| Documentation site | `Documentation` workflow on the push to `main` | `docs/`, including the regenerated release notes |

[docs/contributing/README.md](../../../docs/contributing/README.md#commits-and-releases) is the source of truth for
the release rules. Read it and `.github/workflows/release.yml` and `maven-central.yml` before starting; if they
disagree with this skill, follow them and report the difference.

## Ground rules

- **Irreversible steps.** Pushing the tag publishes the GitHub release and the npm package. npm never accepts the same
  version twice, even after unpublishing. Publishing in Central Portal is permanent. Before pushing the tag, confirm the
  version and the go-ahead with the user, unless the request already named both.
- **Identity.** Before the first commit, check that `git config user.name` and `user.email` are the user's. Before
  the first `gh` write, check `gh auth status`. If either is missing or uncertain, ask. Never add agent, AI tool or
  provider credits, `Co-authored-by` trailers or signatures to commits, tags, pull requests or comments.
- **Names.** Branch `chore/release-<version>`, commit and pull request title `chore: release <version>`, annotated
  tag `v<version>` with the message `Release <version>`.
- **Never fix a release by moving a tag.** If anything was published from a tag, leave it and release the next patch
  version. A tag may be deleted and recreated only when its `Release` run failed before `gh release create` and before
  `publish-npm`. Check both, and ask the user before deleting it.
- Wait on workflows with `gh run watch <id> --exit-status` in the background rather than sleeping in a loop.

## 1. Preflight

```bash
git switch main
git pull --ff-only
git fetch --tags --prune
git status --short                                  # must be empty
previous=$(git describe --tags --abbrev=0 --match 'v*')
git log --oneline --no-merges "$previous"..HEAD     # what this release contains
gh run list --branch main --workflow Build --limit 1    # latest Build on main must be green
gh release view unreleased --json body --jq .body       # draft notes kept by the Release workflow
```

Stop and report if the tree is dirty, `main` is behind, the latest Build failed, or there is nothing to release.

## 2. Choose the version

`mod_version` follows Semantic Versioning. Supported contracts: tool names and schemas, system properties, the
instance registry format, the endpoint path and the extension API.

- **Breaking change:** a commit with `!` or a `BREAKING CHANGE:` line, or an incompatible contract change. While on
  `0.x`, advance MINOR and name the break in the pull request. From `1.0.0`, advance MAJOR.
- **Feature:** any `feat` commit, or a backward-compatible contract addition. Advance MINOR.
- **Fixes only:** advance PATCH.

Do not rely on commit types alone. Look at the contracts directly:

```bash
git diff "$previous" -- gateway/catalogue.json      # removed tools or properties, new required fields, narrowed types
git diff "$previous" -- docs/configuration.md src/main/kotlin/io/github/ummamute/driver/server/ \
    src/main/kotlin/io/github/ummamute/driver/api/
```

Propose the version with a one-line reason and the notable changes. Then check that it is unused on every channel:

```bash
git rev-parse -q --verify "refs/tags/v$version"                 # must print nothing
npm view "minecraft-driver-mcp@$version" version                # must report 404
group=$(sed -n 's/^maven_group=//p' gradle.properties | tr . /)
curl -s -o /dev/null -w '%{http_code}\n' \
  "https://repo1.maven.org/maven2/$group/minecraft-driver-mcp/$version+<minecraft>/"    # must be 404
```

## 3. Release pull request

```bash
git switch -c "chore/release-$version" origin/main
```

Update every place that names the current release, then search for the previous version to catch new ones:

| File | Change |
| --- | --- |
| `gradle.properties` | `mod_version=<version>` |
| `gateway/package.json`, `gateway/package-lock.json` | `npm --prefix gateway version <version> --no-git-tag-version` updates both |
| `gateway/catalogue.json` | top-level `"version"` |
| `README.md` | `Version <version> targets Minecraft ...` |
| `llms.txt` | `The current release version is <version>.` |
| `docs/release-notes.md` | regenerate with `npm run changelog` after the bump |

```bash
git grep -n -F "${previous#v}" -- . ':!docs/release-notes.md' ':!docs/contributing/known-issues.md'
```

Entries that record what an older version was checked with, as in `docs/contributing/known-issues.md`, stay as
they are.

`npm run changelog` names the new section after `mod_version` and replaces "Unreleased". Read the section. Each entry
is a commit subject, so a bad entry can't be edited in place. Raise it with the user, who can fix it with a follow-up
commit before the release. Text outside the `changelog` markers is hand-written and can be edited.

Validate before committing:

```bash
./gradlew build                                       # tests, lint, jar; also checks catalogue == mod version
npm --prefix gateway test                             # rejects a package/catalogue/mod version mismatch
npm run test:release
node scripts/changelog.mjs --check
./gradlew publishMavenPublicationToVerificationRepository
node scripts/check-maven-publication.mjs
./gradlew -p verification/maven-consumer compileJava jar verifyDriverRuntime
./gradlew -p verification/maven-consumer compileJava jar verifyDriverRuntime -PpomOnly --rerun-tasks
npm run docs:build
```

Then run the smoke check from [docs/contributing/testing.md](../../../docs/contributing/testing.md):
`./gradlew :<minecraft>:runClient`, then `initialize`, `tools/list` and `mc_get_state`. The bump renames the jar,
so this counts as a packaging change. If the check can't be run, say so in the pull request.

Commit, push and open the pull request:

```bash
git add -A
git commit -m "chore: release $version"
git push -u origin "chore/release-$version"
gh pr create --base main --title "chore: release $version" --body-file <body.md>
```

Body: the version and why (breaking/feature/fix), the highlights with links to their pull requests, breaking changes
and what users must change, and the validation actually run, including what was skipped. Wait for `Build result`.
Rerun a failure once only if it is clearly unrelated, and say so. Merge with a merge commit like earlier releases:

```bash
gh pr merge <number> --merge --delete-branch
```

## 4. Tag

Confirm the go-ahead first (see Ground rules). Then tag the merge commit on `main`:

```bash
git switch main
git pull --ff-only
test "$(sed -n 's/^mod_version=//p' gradle.properties)" = "$version"
git log -1 --format='%h %s'                           # the merge of the release pull request
git tag -a "v$version" -m "Release $version"
git push origin "v$version"
```

## 5. GitHub release and npm

```bash
gh run list --workflow release.yml --branch "v$version" --limit 1 --json databaseId,status
gh run watch <id> --exit-status
```

The run checks that the tag matches `mod_version`, builds the jar, tests and packs the gateway, creates the release,
deletes the `unreleased` draft, then `publish-npm` publishes the attached archive. Check the result:

```bash
gh release view "v$version" --json isDraft,isLatest,assets --jq '{isDraft, isLatest, assets: [.assets[].name]}'
npm view "minecraft-driver-mcp@$version" version dist.integrity
```

Expect `minecraft-driver-mcp-<version>+<minecraft>.jar` for each `versions/*` directory and
`minecraft-driver-mcp-<version>.tgz`. If only `publish-npm` failed, rerun it with `gh run rerun <id> --failed`. The
publish script skips an archive npm already has, and it fails if npm holds a different one.

`gh release list` can show older drafts titled "Unreleased" besides the live `unreleased` draft. Report them and
delete them only with the user's agreement.

## 6. Maven Central

```bash
gh workflow run maven-central.yml --ref main -f tag="v$version"
gh run list --workflow maven-central.yml --limit 1 --json databaseId,status,createdAt
gh run watch <id> --exit-status
```

If the run waits on the `maven-central` environment, ask the user to approve it in GitHub. The workflow verifies the
tag, rebuilds and checks the artifacts, signs them and uploads a deployment without releasing it.

The agent cannot do the final publish. It needs the user's Central Portal account. Hand it over with these steps:

1. Open https://central.sonatype.com/publishing/deployments while signed in to the account that owns the namespace.
2. Find the deployment for `<group>:minecraft-driver-mcp:<version>+<minecraft>` with status `VALIDATED`.
3. Check its components: jar, sources, Javadoc, POM and module, each with a signature.
4. Choose **Publish**. This is permanent. Use **Drop** instead if anything looks wrong, then fix it and stage again.

Once the user has published, poll until the files are served. This usually takes 10 to 30 minutes:

```bash
base="https://repo1.maven.org/maven2/$group/minecraft-driver-mcp/$version+<minecraft>"
until curl -sf -o /dev/null "$base/minecraft-driver-mcp-$version+<minecraft>.pom"; do sleep 60; done
curl -s "$base/" | grep -o 'href="[^"]*"'             # jar, sources, javadoc, pom, module, each with .asc
```

`maven-metadata.xml` and the search index update later, and that delay is normal.

## 7. Finish

- Check that the `Documentation` run for the release merge on `main` passed (`gh run list --workflow docs.yml`).
- Mention open issues or pull requests that the release resolves or that were waiting on it.
- Report to the user: version and reason, links to the pull request, tag, GitHub release, npm package
  (`https://www.npmjs.com/package/minecraft-driver-mcp/v/<version>`), Maven Central directory and docs site, and
  anything skipped or still pending, such as the Central Portal publish.
