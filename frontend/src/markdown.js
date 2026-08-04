import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/common'

// Only expose a conservative set of filename mappings. highlight.js still
// validates the final language name before it is used for highlighting.
const EXTENSION_LANGUAGES = {
  c: 'c',
  cc: 'cpp',
  cpp: 'cpp',
  cs: 'csharp',
  css: 'css',
  go: 'go',
  h: 'c',
  hpp: 'cpp',
  html: 'xml',
  java: 'java',
  js: 'javascript',
  jsx: 'javascript',
  json: 'json',
  kt: 'kotlin',
  kts: 'kotlin',
  md: 'markdown',
  mjs: 'javascript',
  properties: 'ini',
  py: 'python',
  rb: 'ruby',
  rs: 'rust',
  scss: 'scss',
  sh: 'bash',
  sql: 'sql',
  svg: 'xml',
  ts: 'typescript',
  tsx: 'typescript',
  vue: 'xml',
  xml: 'xml',
  yaml: 'yaml',
  yml: 'yaml',
}

const LANGUAGE_ALIASES = {
  bash: 'bash',
  console: 'shell',
  javascript: 'javascript',
  js: 'javascript',
  jsonc: 'json',
  jsx: 'javascript',
  md: 'markdown',
  plaintext: 'plaintext',
  py: 'python',
  python: 'python',
  sh: 'bash',
  shell: 'shell',
  text: 'plaintext',
  ts: 'typescript',
  tsx: 'typescript',
  typescript: 'typescript',
  vue: 'xml',
  yml: 'yaml',
}

function normalizeLanguage(language) {
  const normalized = String(language || '').trim().toLowerCase().replace(/^language-/, '')
  return LANGUAGE_ALIASES[normalized] || normalized
}

function safeLanguageClass(language) {
  const normalized = normalizeLanguage(language)
  return normalized && /^[a-z0-9-]+$/.test(normalized) ? normalized : ''
}

function escapeHtml(value) {
  return String(value || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function highlightValue(value, language = '') {
  const source = String(value || '')
  const normalized = normalizeLanguage(language)
  try {
    if (normalized && hljs.getLanguage(normalized)) {
      return {
        html: hljs.highlight(source, { language: normalized, ignoreIllegals: true }).value,
        language: normalized,
      }
    }
    if (!normalized && source.trim()) {
      const detected = hljs.highlightAuto(source)
      return { html: detected.value, language: detected.language || '' }
    }
  } catch {
    // Highlighting is presentation-only. A failed grammar must never hide the
    // model response or a file preview.
  }
  return { html: escapeHtml(source), language: normalized }
}

const markdown = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: false,
  typographer: true,
  highlight(source, language) {
    const highlighted = highlightValue(source, language)
    const languageClass = safeLanguageClass(highlighted.language)
    const className = languageClass ? `hljs language-${languageClass}` : 'hljs'
    const label = languageClass || 'code'
    return `<pre class="markdown-code-block ${className}" data-language="${label}"><button type="button" class="markdown-code-copy" data-copy-code="true" aria-label="复制代码块">复制</button><code>${highlighted.html}</code></pre>`
  },
})

export function renderMarkdown(value) {
  if (value == null || value === '') return ''
  return markdown.render(String(value))
}

export function highlightCode(value, language = '') {
  return highlightValue(value, language).html
}

export function languageFromPath(path) {
  const name = String(path || '').split(/[\\/]/).pop()?.toLowerCase() || ''
  if (name === 'dockerfile') return 'dockerfile'
  if (name === 'makefile') return 'makefile'
  const extension = name.includes('.') ? name.split('.').pop() : ''
  return EXTENSION_LANGUAGES[extension] || ''
}

export function languageLabel(language, path = '') {
  const resolved = normalizeLanguage(language) || languageFromPath(path)
  return resolved || 'text'
}
