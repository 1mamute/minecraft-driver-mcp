#!/usr/bin/env node
// Builds the changelog from the Conventional Commit history, so nobody writes entries by hand.
//
//   node scripts/changelog.mjs              update docs/release-notes.md
//   node scripts/changelog.mjs --notes FILE write the notes of the current section to FILE (the GitHub release body)
//   node scripts/changelog.mjs --check      exit 1 when docs/release-notes.md is out of date
//
// The section follows git state. Commits after the newest `v*` tag form "Unreleased", or the section of `mod_version`
// once that has been bumped past the tag. A commit that is the newest tag
// forms the section of that version, taken from the previous tag. With no tag yet, all commits form the section named
// by `mod_version` in gradle.properties. Only the text between the `changelog` markers is replaced, so the text around
// them (intro, known limits) stays hand-written.
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const notesFile = join(root, 'docs', 'release-notes.md')

const GROUPS = [
  ['breaking', 'Breaking changes'],
  ['feat', 'Features'],
  ['fix', 'Fixes'],
  ['perf', 'Performance'],
  ['refactor', 'Refactoring'],
  ['docs', 'Documentation'],
  ['test', 'Tests'],
  ['build', 'Build'],
  ['ci', 'Build'],
  ['chore', 'Chores'],
]

function git(...args) {
  return execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim()
}

function tryGit(...args) {
  try {
    return git(...args)
  } catch {
    return ''
  }
}

function modVersion() {
  const properties = readFileSync(join(root, 'gradle.properties'), 'utf8')
  return /^mod_version\s*=\s*(\S+)/m.exec(properties)[1]
}

function repositoryUrl() {
  const remote = tryGit('remote', 'get-url', 'origin')
  const match = /github\.com[:/](.+?)(?:\.git)?$/.exec(remote)
  return match ? `https://github.com/${match[1]}` : ''
}

/** Decides the section name and the commit range from the tags. */
function resolveSection() {
  const tags = tryGit('tag', '--list', 'v*', '--sort=-v:refname').split('\n').filter(Boolean)
  const head = git('rev-parse', 'HEAD')
  if (tags.length === 0) {
    return { name: modVersion(), range: 'HEAD' }
  }
  const newest = tags[0]
  if (git('rev-parse', `${newest}^{commit}`) === head) {
    const previous = tags[1]
    return { name: newest.replace(/^v/, ''), range: previous ? `${previous}..HEAD` : 'HEAD' }
  }
  // Once `mod_version` is bumped past the newest tag, the commits already form that release.
  const bumped = modVersion() !== newest.replace(/^v/, '')
  return { name: bumped ? modVersion() : 'Unreleased', range: `${newest}..HEAD` }
}

function readCommits(range) {
  const raw = git('log', '--reverse', '--no-merges', '--format=%H%x1f%s%x1f%b%x1e', range)
  return raw
    .split('\x1e')
    .map((record) => record.trim())
    .filter(Boolean)
    .map((record) => {
      const [hash, subject, body] = record.split('\x1f')
      return { hash, subject: subject.trim(), body: (body ?? '').trim() }
    })
}

const TYPED = /^(\w+)(?:\([^)]*\))?(!)?: (.+)$/
const PULL_REQUEST = /\s+\(#(\d+)\)$/

/** A commit is one entry, or, for a squash titled with a branch name, one entry per typed bullet in its body. */
function entriesOf(commit) {
  const pr = PULL_REQUEST.exec(commit.subject)?.[1]
  const subject = commit.subject.replace(PULL_REQUEST, '')
  const typed = TYPED.exec(subject)
  const breaking = /^BREAKING[ -]CHANGE:/m.test(commit.body) || Boolean(typed?.[2])
  if (typed) {
    return [{ type: typed[1].toLowerCase(), text: typed[3], pr, breaking }]
  }
  const bullets = [...commit.body.matchAll(/^\* (\w+)(?:\([^)]*\))?: (.+)$/gm)]
  return bullets.map((bullet) => ({ type: bullet[1].toLowerCase(), text: bullet[2], pr, breaking: false }))
}

function format(entry, repository) {
  const text = entry.text.replace(/(?<!`)\b(mc_\w+|driver\.\w+|hold_ticks)\b(?!`)/g, '`$1`')
  const link = entry.pr && repository ? ` ([#${entry.pr}](${repository}/pull/${entry.pr}))` : ''
  return `- ${text.charAt(0).toUpperCase()}${text.slice(1)}${link}`
}

function renderGroups(commits, repository) {
  const seen = new Set()
  const byGroup = new Map()
  for (const commit of commits) {
    for (const entry of entriesOf(commit)) {
      const key = entry.text.toLowerCase()
      const group = entry.breaking ? 'Breaking changes' : GROUPS.find(([type]) => type === entry.type)?.[1]
      if (!group || seen.has(key)) {
        continue
      }
      seen.add(key)
      byGroup.set(group, [...(byGroup.get(group) ?? []), format(entry, repository)])
    }
  }
  const order = [...new Set(GROUPS.map(([, title]) => title))]
  return order
    .filter((title) => byGroup.has(title))
    .map((title) => `### ${title}\n\n${byGroup.get(title).join('\n')}`)
    .join('\n\n')
}

function replaceSection(document, name, content) {
  const open = `<!-- changelog:${name} -->`
  const close = `<!-- /changelog:${name} -->`
  const block = `${open}
${content}
${close}`
  const start = document.indexOf(open)
  const end = document.indexOf(close)
  if (start !== -1 && end > start) {
    return document.slice(0, start) + block + document.slice(end + close.length)
  }
  // A new section goes above the newest one. A released version replaces the "Unreleased" section.
  const base = name === 'Unreleased' ? document : dropSection(document, 'Unreleased')
  const section = `## ${name}

${block}

`
  const at = base.search(/^## /m)
  return at === -1 ? `${base.trimEnd()}

${section.trimEnd()}
` : base.slice(0, at) + section + base.slice(at)
}

function dropSection(document, name) {
  const pattern = new RegExp(`^## ${name}\\n[\\s\\S]*?(?=^## |(?![\\s\\S]))`, 'm')
  return document.replace(pattern, '')
}

function main() {
  const args = process.argv.slice(2)
  const section = resolveSection()
  const content = renderGroups(readCommits(section.range), repositoryUrl())
  const notesAt = args.indexOf('--notes')
  if (notesAt !== -1) {
    writeFileSync(args[notesAt + 1], `${content}\n`)
    return
  }
  const current = readFileSync(notesFile, 'utf8')
  const updated = replaceSection(current, section.name, content)
  if (args.includes('--check')) {
    if (updated !== current) {
      console.error('docs/release-notes.md is out of date; run `node scripts/changelog.mjs`.')
      process.exit(1)
    }
    return
  }
  writeFileSync(notesFile, updated)
  console.log(`Updated the "${section.name}" section of docs/release-notes.md`)
}

main()
