<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import JsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import CssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import HtmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import TsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

const workerEnvironment = globalThis.MonacoEnvironment || {}
if (!globalThis.MonacoEnvironment) {
  globalThis.MonacoEnvironment = workerEnvironment
}
globalThis.MonacoEnvironment.getWorker = (_moduleId, label) => {
  if (label === 'json') return new JsonWorker()
  if (label === 'css' || label === 'scss' || label === 'less') return new CssWorker()
  if (label === 'html' || label === 'handlebars' || label === 'razor') return new HtmlWorker()
  if (label === 'typescript' || label === 'javascript') return new TsWorker()
  return new EditorWorker()
}

const props = defineProps({
  modelValue: { type: String, default: '' },
  path: { type: String, default: '' },
  language: { type: String, default: 'plaintext' },
  readonly: { type: Boolean, default: false },
  diff: { type: Boolean, default: false },
  original: { type: String, default: '' },
  modified: { type: String, default: '' },
  height: { type: String, default: '100%' },
})

const emit = defineEmits(['update:modelValue', 'change', 'save', 'ready'])
const container = ref(null)
let editor
let originalModel
let modifiedModel
let contentListener
let themeObserver
let editorId = 0
let syncingModel = false

const DARK_THEME = 'ming-harness-dark'
const LIGHT_THEME = 'ming-harness-light'

function ensureThemes() {
  monaco.editor.defineTheme(DARK_THEME, {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: 'comment', foreground: '7886A2', fontStyle: 'italic' },
      { token: 'keyword', foreground: 'C5A8FF' },
      { token: 'string', foreground: '7FE1B0' },
      { token: 'number', foreground: 'F3C77B' },
      { token: 'type', foreground: '85D9FF' },
      { token: 'delimiter', foreground: 'B9C5DB' },
    ],
    colors: {
      'editor.background': '#0b1222',
      'editor.foreground': '#dbe5fb',
      'editorLineNumber.foreground': '#53627f',
      'editorLineNumber.activeForeground': '#b7adff',
      'editorCursor.foreground': '#8d7cff',
      'editor.selectionBackground': '#37326a',
      'editor.inactiveSelectionBackground': '#252448',
      'editor.lineHighlightBackground': '#111b30',
      'editorIndentGuide.background': '#1b2942',
      'editorIndentGuide.activeBackground': '#34466c',
      'editorWidget.background': '#101a2d',
      'editorWidget.border': '#263654',
      'editorSuggestWidget.background': '#101a2d',
      'editorSuggestWidget.border': '#263654',
      'scrollbarSlider.background': '#334362aa',
      'scrollbarSlider.hoverBackground': '#4b5e83cc',
      'diffEditor.insertedTextBackground': '#1e704a55',
      'diffEditor.removedTextBackground': '#873b4d55',
      'diffEditor.insertedLineBackground': '#164e3766',
      'diffEditor.removedLineBackground': '#572b3a66',
    },
  })
  monaco.editor.defineTheme(LIGHT_THEME, {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'comment', foreground: '687895', fontStyle: 'italic' },
      { token: 'keyword', foreground: '6046b9' },
      { token: 'string', foreground: '18734b' },
      { token: 'number', foreground: '986718' },
      { token: 'type', foreground: '08739b' },
    ],
    colors: {
      'editor.background': '#fbfcff',
      'editor.foreground': '#27334b',
      'editorLineNumber.foreground': '#9aa6b9',
      'editorLineNumber.activeForeground': '#5949c6',
      'editorCursor.foreground': '#6859db',
      'editor.selectionBackground': '#dcd8ff',
      'editor.lineHighlightBackground': '#f1f3fb',
      'editorIndentGuide.background': '#e2e6f0',
      'editorIndentGuide.activeBackground': '#c6cce0',
      'editorWidget.background': '#ffffff',
      'editorWidget.border': '#d8deea',
      'scrollbarSlider.background': '#b9c3d399',
      'scrollbarSlider.hoverBackground': '#8c9ab3aa',
      'diffEditor.insertedTextBackground': '#bfead366',
      'diffEditor.removedTextBackground': '#ffd4d866',
      'diffEditor.insertedLineBackground': '#e6f7ed',
      'diffEditor.removedLineBackground': '#fff0f1',
    },
  })
}

function currentTheme() {
  return document.documentElement.dataset.theme === 'light' ? LIGHT_THEME : DARK_THEME
}

function editorOptions() {
  return {
    automaticLayout: true,
    ariaLabel: props.diff ? '代码差异对比编辑器' : '代码编辑器',
    contextmenu: true,
    folding: true,
    foldingHighlight: true,
    fontFamily: 'DM Mono, SFMono-Regular, Consolas, monospace',
    fontLigatures: false,
    fontSize: 12,
    glyphMargin: false,
    lineDecorationsWidth: 8,
    lineNumbers: 'on',
    minimap: { enabled: false },
    padding: { top: 12, bottom: 12 },
    renderLineHighlight: 'line',
    renderWhitespace: 'selection',
    roundedSelection: false,
    scrollBeyondLastLine: false,
    smoothScrolling: true,
    stickyScroll: { enabled: false },
    tabSize: 2,
    wordWrap: 'off',
  }
}

function modelUri(kind) {
  editorId += 1
  const safePath = encodeURIComponent(props.path || 'untitled')
  return monaco.Uri.parse(`inmemory://ming-harness/${kind}/${editorId}/${safePath}`)
}

function disposeModels() {
  originalModel?.dispose()
  modifiedModel?.dispose()
  originalModel = null
  modifiedModel = null
}

function createModels() {
  const language = props.language || 'plaintext'
  if (props.diff) {
    originalModel = monaco.editor.createModel(props.original || '', language, modelUri('original'))
    modifiedModel = monaco.editor.createModel(props.modified || '', language, modelUri('modified'))
  } else {
    modifiedModel = monaco.editor.createModel(props.modelValue || '', language, modelUri('editor'))
  }
}

function mountEditor() {
  if (!container.value) return
  ensureThemes()
  monaco.editor.setTheme(currentTheme())
  createModels()
  if (props.diff) {
    editor = monaco.editor.createDiffEditor(container.value, {
      ...editorOptions(),
      readOnly: true,
      originalEditable: false,
      renderOverviewRuler: true,
      renderSideBySide: true,
      diffCodeLens: false,
      hideUnchangedRegions: { enabled: true, revealLineThreshold: 3 },
    })
    editor.setModel({ original: originalModel, modified: modifiedModel })
  } else {
    editor = monaco.editor.create(container.value, {
      ...editorOptions(),
      readOnly: props.readonly,
    })
    editor.setModel(modifiedModel)
    contentListener = editor.onDidChangeModelContent(() => {
      if (syncingModel) return
      const value = editor.getValue()
      emit('update:modelValue', value)
      emit('change', value)
    })
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => emit('save', editor.getValue()))
  }
  themeObserver = new MutationObserver(() => monaco.editor.setTheme(currentTheme()))
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
  emit('ready', editor)
}

function remountModels() {
  if (!editor) return
  const previousValue = props.diff ? null : editor.getValue()
  if (props.diff) {
    disposeModels()
    createModels()
    editor.setModel({ original: originalModel, modified: modifiedModel })
  } else if (props.language !== undefined && modifiedModel) {
    monaco.editor.setModelLanguage(modifiedModel, props.language || 'plaintext')
    if (previousValue !== props.modelValue && previousValue !== null) {
      syncingModel = true
      try {
        modifiedModel.setValue(props.modelValue || '')
      } finally {
        syncingModel = false
      }
    }
  }
}

watch(() => [props.language, props.path, props.diff], remountModels)
watch(() => props.modelValue, (value) => {
  if (!props.diff && modifiedModel && editor && editor.getValue() !== (value || '')) {
    const position = editor.getPosition()
    syncingModel = true
    try {
      modifiedModel.setValue(value || '')
    } finally {
      syncingModel = false
    }
    if (position) editor.setPosition(position)
  }
})
watch(() => [props.original, props.modified], () => {
  if (!props.diff || !originalModel || !modifiedModel) return
  if (originalModel.getValue() !== (props.original || '')) originalModel.setValue(props.original || '')
  if (modifiedModel.getValue() !== (props.modified || '')) modifiedModel.setValue(props.modified || '')
})

defineExpose({
  getValue: () => editor && !props.diff ? editor.getValue() : props.modified || '',
  focus: () => editor?.focus(),
  layout: () => editor?.layout(),
  copy: async () => {
    const value = editor && !props.diff ? editor.getValue() : props.modified || ''
    await navigator.clipboard.writeText(value)
  },
})

onMounted(mountEditor)
onBeforeUnmount(() => {
  contentListener?.dispose()
  themeObserver?.disconnect()
  editor?.dispose()
  disposeModels()
})
</script>

<template>
  <div ref="container" class="monaco-editor-host" :style="{ height }"></div>
</template>
