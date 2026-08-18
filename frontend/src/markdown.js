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

// 教育 Agent 的分层提示先以普通 Markdown 保存，历史消息也可能已经使用了这种格式。
// 在进入通用 Markdown 渲染前把它拆成结构化片段，交给 Vue 使用原生 <details> 展示，
// 这样既不需要信任模型生成 HTML，也能让旧消息和新消息拥有一致的折叠行为。
const LEARNING_HINT_SECTION_PATTERN = /^\s*(?:#{1,6}\s*)?(?:💡\s*)?(?:\*\*)?分层提示(?:\*\*)?\s*(?:[（(]([^）)]*)[）)])?\s*[：:]?\s*$/
const LEARNING_HINT_PATTERN = /^\s*(?:[-*+]\s+)?(?:\*\*)?\s*提示\s*(\d+)\s*(?:[（(]([^）)]*)[）)])?\s*(?:\*\*)?\s*(?:[：:]\s*)?(.*)$/

function learningHintSectionHeader(line) {
  return String(line || '').match(LEARNING_HINT_SECTION_PATTERN)
}

function learningHintLine(line) {
  return String(line || '').match(LEARNING_HINT_PATTERN)
}

function isMarkdownContinuationLine(line) {
  return /^\s{2,}|^\s*(?:[-*+]\s+|\d+[.)]\s+)/.test(String(line || ''))
}

function parseLearningHintsAfterHeader(lines, startIndex) {
  let cursor = startIndex
  while (cursor < lines.length && !String(lines[cursor] || '').trim()) cursor += 1
  if (cursor >= lines.length || !learningHintLine(lines[cursor])) return null

  const hints = []
  let current = null
  let separatedByBlank = false
  for (; cursor < lines.length; cursor += 1) {
    const line = String(lines[cursor] || '')
    const match = learningHintLine(line)
    if (match) {
      if (current) hints.push(current)
      current = {
        title: `提示 ${match[1]}${match[2] ? ` · ${match[2].trim()}` : ''}`,
        content: [match[3] || ''],
      }
      separatedByBlank = false
      continue
    }
    if (!current) break
    if (/^\s*(?:---+|___+|\*\*\*+)\s*$/.test(line)
      || /^\s*#{1,6}\s+/.test(line)) {
      break
    }
    if (!line.trim()) {
      current.content.push('')
      separatedByBlank = true
      continue
    }
    // 每层提示通常是一个独立段落。连续的普通文本属于提示正文；空行后出现的
    // 普通段落则表示提示区结束，避免把后续“下一步行动”吞进最后一层提示。
    if (separatedByBlank && !isMarkdownContinuationLine(line)) break
    current.content.push(line)
    separatedByBlank = false
  }
  if (current) hints.push(current)
  if (!hints.length) return null
  return { hints, endIndex: cursor - 1 }
}

export function splitLearningHintSections(value) {
  const source = String(value || '')
  if (!source.trim()) return []
  const lines = source.split(/\r?\n/)
  const segments = []
  let markdownLines = []
  const flushMarkdown = () => {
    const content = markdownLines.join('\n')
    if (content.trim()) segments.push({ type: 'markdown', content })
    markdownLines = []
  }

  for (let index = 0; index < lines.length; index += 1) {
    const section = learningHintSectionHeader(lines[index])
    if (!section) {
      markdownLines.push(lines[index])
      continue
    }
    const parsed = parseLearningHintsAfterHeader(lines, index + 1)
    if (!parsed) {
      markdownLines.push(lines[index])
      continue
    }
    flushMarkdown()
    segments.push({
      type: 'learning-hints',
      note: section[1]?.trim() || '先尝试，卡住再展开',
      hints: parsed.hints.map((hint) => ({
        title: hint.title,
        content: hint.content.join('\n').trim(),
      })),
    })
    index = parsed.endIndex
  }
  flushMarkdown()
  return segments
}

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
