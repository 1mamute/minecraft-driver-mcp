import { defineConfig } from 'vitepress'
import { repoLinks } from './repo-links'

const repo = 'https://github.com/1mamute/minecraft-driver-mcp'

// The site is built from the same Markdown files that GitHub renders, so the files keep GitHub-style links.
// The README files become section index pages, and `repoLinks` turns links that leave docs/ into GitHub links.
const rewrites = {
  'contributing/README.md': 'contributing/index.md',
}

export default defineConfig({
  title: 'Minecraft Driver MCP',
  description: 'Let an AI assistant drive a real Minecraft client to test and debug Fabric mods.',
  base: '/minecraft-driver-mcp/',
  lang: 'en-US',
  cleanUrls: true,
  lastUpdated: true,
  // docs/README.md indexes the docs on GitHub; on the site the sidebar does that job.
  srcExclude: ['README.md'],
  rewrites,
  sitemap: { hostname: 'https://1mamute.github.io/minecraft-driver-mcp/' },
  head: [['meta', { name: 'theme-color', content: '#000000' }]],

  markdown: {
    theme: { light: 'github-light', dark: 'github-dark' },
    config: (md) => md.use(repoLinks, { repo, rewrites }),
  },

  themeConfig: {
    nav: [
      { text: 'Guide', link: '/introduction', activeMatch: '^/(introduction|installation|getting-started|debugging-mods)' },
      { text: 'Tool reference', link: '/tools' },
      { text: 'Contributing', link: '/contributing/', activeMatch: '^/contributing/' },
    ],

    sidebar: [
      {
        text: 'Guide',
        items: [
          { text: 'Introduction', link: '/introduction' },
          { text: 'Installation', link: '/installation' },
          { text: 'Getting started', link: '/getting-started' },
          { text: 'Debugging mods', link: '/debugging-mods' },
        ],
      },
      {
        text: 'Reference',
        items: [
          { text: 'Tool reference', link: '/tools' },
          { text: 'Configuration', link: '/configuration' },
          { text: 'Several clients', link: '/multiple-clients' },
          { text: 'Extension API', link: '/extension-api' },
          { text: 'Known issues', link: '/known-issues' },
        ],
      },
      {
        text: 'Contributing',
        items: [
          { text: 'Contributor guide', link: '/contributing/' },
          { text: 'Architecture', link: '/contributing/architecture' },
          { text: 'Extending the mod', link: '/contributing/extending' },
          { text: 'Testing', link: '/contributing/testing' },
          { text: 'Code style', link: '/contributing/code-style' },
          { text: 'Dependencies', link: '/contributing/dependencies' },
          { text: 'Verification status', link: '/contributing/known-issues' },
        ],
      },
    ],

    outline: { level: [2, 3], label: 'On this page' },
    search: { provider: 'local' },
    socialLinks: [{ icon: 'github', link: repo }],

    editLink: {
      // The home page is the repository README, included into docs/index.md. VitePress serializes this function to
      // the browser, so it cannot use `repo` or other names from this module.
      pattern: ({ filePath }) =>
        `https://github.com/1mamute/minecraft-driver-mcp/edit/main/${filePath === 'index.md' ? 'README.md' : `docs/${filePath}`}`,
      text: 'Edit this page on GitHub',
    },

    footer: {
      message: 'Released under the MIT License. Not an official Minecraft product; not affiliated with Mojang or Microsoft.',
    },
  },
})
