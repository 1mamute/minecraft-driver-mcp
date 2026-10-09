import path from 'node:path'
import type MarkdownIt from 'markdown-it'

interface RepoLinkOptions {
  /** Repository URL, used for links to files outside docs/. */
  repo: string
  /** VitePress rewrites, source path to page path, both relative to docs/. */
  rewrites: Record<string, string>
}

/**
 * Rewrites the GitHub-style relative links in the Markdown sources so they work on the site.
 *
 * A link to a Markdown file inside docs/ becomes an absolute page path (`/tools#mc_click`), which also applies the
 * rewrites. A link to anything else in the repository (source files, `AGENTS.md`, `LICENSE`) becomes a GitHub link.
 * The home page includes the repository README, whose links are relative to the repository root, not to docs/.
 */
export function repoLinks(md: MarkdownIt, options: RepoLinkOptions): void {
  md.core.ruler.push('repo_links', (state) => {
    const page: string = state.env.relativePath ?? ''
    const baseDir = page === 'index.md' ? '' : path.posix.join('docs', path.posix.dirname(page))
    for (const token of state.tokens) {
      for (const child of token.children ?? []) {
        if (child.type !== 'link_open') continue
        const href = child.attrGet('href')
        if (href && isRepoRelative(href)) child.attrSet('href', resolve(href, baseDir, options))
      }
    }
  })
}

function isRepoRelative(href: string): boolean {
  return !/^([a-z][a-z0-9+.-]*:|#|\/)/i.test(href)
}

function resolve(href: string, baseDir: string, options: RepoLinkOptions): string {
  const [target, hash] = splitHash(href)
  const repoPath = path.posix.normalize(path.posix.join(baseDir, target))
  if (!repoPath.startsWith('docs/') || !repoPath.endsWith('.md')) {
    return `${options.repo}/blob/main/${repoPath}${hash}`
  }
  return pagePath(repoPath.slice('docs/'.length), options.rewrites) + hash
}

function splitHash(href: string): [string, string] {
  const index = href.indexOf('#')
  return index < 0 ? [href, ''] : [href.slice(0, index), href.slice(index)]
}

function pagePath(source: string, rewrites: Record<string, string>): string {
  // docs/README.md is excluded from the site; the home page takes its place.
  if (source === 'README.md') return '/'
  const page = rewrites[source] ?? source
  return '/' + page.replace(/(^|\/)index\.md$/, '$1').replace(/\.md$/, '')
}
