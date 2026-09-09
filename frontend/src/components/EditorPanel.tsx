import { useRef, useState } from 'react'
import {
  Check,
  Code2,
  FileText,
  Hash,
  LoaderCircle,
  MessageSquarePlus,
  PanelLeftClose,
  Save,
  Sparkles,
  Type,
} from 'lucide-react'
import MarkdownPreview from './MarkdownPreview'
import { byteLength, sectionTextAtPosition, formatBytes } from '../markdown'
import type { DraftDocument, SelectionContext } from '../types'

interface EditorPanelProps {
  document: DraftDocument | null
  loading: boolean
  error: string | null
  selectionCount: number
  onChange: (content: string) => void
  onSave: () => void
  onRetry: () => void
  onAddSelection: (selection: Omit<SelectionContext, 'id'>) => void
}

function SyncBadge({ document }: { document: DraftDocument }) {
  const status = document.syncStatus
  if (status === 'saving') {
    return <span className="sync-badge is-saving"><LoaderCircle className="spin" size={13} />同步中</span>
  }
  if (status === 'dirty' || status === 'error') {
    return <span className={`sync-badge ${status === 'error' ? 'is-error' : 'is-dirty'}`}><span className="sync-badge-dot" />未同步</span>
  }
  return <span className="sync-badge is-synced"><Check size={13} />已同步</span>
}

function EditorPanel({
  document,
  loading,
  error,
  selectionCount,
  onChange,
  onSave,
  onRetry,
  onAddSelection,
}: EditorPanelProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const [selectionRange, setSelectionRange] = useState({ start: 0, end: 0 })

  const selectedText = document
    ? document.content.slice(selectionRange.start, selectionRange.end)
    : ''
  const canAddSelection = Boolean(selectedText.trim()) && Boolean(document)

  const updateSelection = () => {
    const textarea = textareaRef.current
    if (!textarea) return
    setSelectionRange({ start: textarea.selectionStart, end: textarea.selectionEnd })
  }

  const handleAddSelection = () => {
    if (!document || !canAddSelection) return
    onAddSelection({
      documentId: document.id,
      originalText: selectedText,
      sectionText: sectionTextAtPosition(document.content, selectionRange.start),
    })
  }

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
      event.preventDefault()
      onSave()
    }
  }

  if (loading) {
    return (
      <section className="editor-panel panel-shell" aria-busy="true">
        <div className="panel-loading">
          <LoaderCircle className="spin" size={23} />
          <p>正在打开文档…</p>
          <span>正在读取最新 Markdown 正文</span>
        </div>
      </section>
    )
  }

  if (error) {
    return (
      <section className="editor-panel panel-shell">
        <div className="panel-empty error-empty">
          <div className="empty-icon is-error"><FileText size={22} /></div>
          <h2>文档打开失败</h2>
          <p>{error}</p>
          <button className="secondary-button" type="button" onClick={onRetry}>重新加载</button>
        </div>
      </section>
    )
  }

  if (!document) {
    return (
      <section className="editor-panel panel-shell">
        <div className="panel-empty">
          <div className="empty-icon"><Sparkles size={22} /></div>
          <h2>选择一篇文档开始</h2>
          <p>上传 Markdown 文件，或从左侧打开已有文档。</p>
        </div>
      </section>
    )
  }

  return (
    <section className="editor-panel panel-shell">
      <header className="editor-header">
        <div className="editor-title-group">
          <div className="editor-file-icon"><FileText size={17} /></div>
          <div>
            <div className="editor-title-line">
              <h1 title={document.fileName}>{document.fileName}</h1>
              <SyncBadge document={document} />
            </div>
            <p className="editor-subtitle">Markdown 文档 · {formatBytes(document.fileSizeBytes || byteLength(document.content))}</p>
          </div>
        </div>
        <button
          className="primary-button save-button"
          type="button"
          disabled={document.syncStatus === 'saving' || (document.syncStatus === 'synced' && !document.syncError)}
          onClick={onSave}
        >
          {document.syncStatus === 'saving' ? <LoaderCircle className="spin" size={15} /> : <Save size={15} />}
          {document.syncStatus === 'saving' ? '保存中' : '保存草稿'}
        </button>
      </header>

      {document.syncError && (
        <div className="sync-error-banner" role="alert">
          <span>{document.syncError}</span>
          <button type="button" onClick={onSave}>重试保存</button>
        </div>
      )}

      <div className="editor-toolbar">
        <div className="format-hints" aria-label="支持的 Markdown 语法">
          <span><Hash size={13} />标题</span>
          <span><Type size={13} />粗体 / 斜体</span>
          <span><Code2 size={13} />代码与列表</span>
        </div>
        <button
          className={`context-button${canAddSelection ? ' is-ready' : ''}`}
          type="button"
          disabled={!canAddSelection}
          onClick={handleAddSelection}
          title={canAddSelection ? '将选中文本加入聊天上下文' : '先在编辑器中选择一段文本'}
        >
          <MessageSquarePlus size={15} />
          加入聊天上下文
          {selectionCount > 0 && <span className="context-count">{selectionCount}</span>}
        </button>
      </div>

      <div className="editor-split">
        <div className="editor-pane">
          <div className="pane-label"><span>编辑</span><span className="pane-note">纯文本</span></div>
          <textarea
            ref={textareaRef}
            className="markdown-input"
            value={document.content}
            onChange={(event) => {
              onChange(event.target.value)
              updateSelection()
            }}
            onSelect={updateSelection}
            onKeyUp={updateSelection}
            onClick={updateSelection}
            onKeyDown={handleKeyDown}
            spellCheck={false}
            aria-label="Markdown 编辑器"
          />
          <div className="editor-footer">
            <span>{document.content.length.toLocaleString()} 字符</span>
            <span>{byteLength(document.content).toLocaleString()} bytes</span>
            {selectedText && <span className="selection-note">已选 {selectedText.length} 字符</span>}
          </div>
        </div>
        <div className="preview-pane">
          <div className="pane-label"><span>实时预览</span><span className="pane-note"><PanelLeftClose size={13} />安全渲染</span></div>
          <div className="preview-scroll">
            <MarkdownPreview content={document.content} />
          </div>
        </div>
      </div>
    </section>
  )
}

export default EditorPanel
