import { useEffect, useMemo, useRef } from 'react'
import {
  Bot,
  ChevronDown,
  FileCode2,
  LoaderCircle,
  MessageCircle,
  Paperclip,
  Send,
  Sparkles,
  X,
} from 'lucide-react'
import MarkdownPreview from './MarkdownPreview'
import OperationCard from './OperationCard'
import type {
  ChatMessage,
  ChatOperation,
  DocumentSummary,
  OperationStatus,
  SelectionContext,
} from '../types'

interface ChatPanelProps {
  messages: ChatMessage[]
  input: string
  sending: boolean
  error: string | null
  documents: DocumentSummary[]
  referencedDocumentIds: number[]
  mentionedDocumentIds: number[]
  selections: SelectionContext[]
  operationBusyKey: string | null
  onInputChange: (value: string) => void
  onSend: () => void
  onRemoveDocumentReference: (documentId: number) => void
  onRemoveSelection: (selectionId: string) => void
  onApplyOperation: (messageId: string, operation: ChatOperation) => void
  onIgnoreOperation: (messageId: string, opId: string) => void
}

function documentLabel(documentId: number, documents: DocumentSummary[]): string {
  return documents.find((document) => document.id === documentId)?.fileName ?? `文档 #${documentId}`
}

function shortText(value: string, maxLength = 34): string {
  const normalized = value.replace(/\s+/g, ' ').trim()
  return normalized.length > maxLength ? `${normalized.slice(0, maxLength)}…` : normalized
}

function ContextShelf({
  documents,
  referencedDocumentIds,
  mentionedDocumentIds,
  selections,
  onRemoveDocumentReference,
  onRemoveSelection,
}: Pick<ChatPanelProps, 'documents' | 'referencedDocumentIds' | 'mentionedDocumentIds' | 'selections' | 'onRemoveDocumentReference' | 'onRemoveSelection'>) {
  const hasContext = referencedDocumentIds.length > 0 || selections.length > 0
  const mentionedOnlyIds = mentionedDocumentIds.filter((id) => !referencedDocumentIds.includes(id))

  return (
    <div className="context-shelf">
      <div className="context-shelf-heading">
        <div className="context-title"><Paperclip size={14} /><span>本轮上下文</span></div>
        <span className="context-description">发送时一并提供给 AI</span>
      </div>
      {!hasContext && mentionedOnlyIds.length === 0 && (
        <p className="context-empty">从左侧引用文档，或在编辑器里选择一段文字加入聊天。</p>
      )}
      {(hasContext || mentionedOnlyIds.length > 0) && (
        <div className="context-chips">
          {referencedDocumentIds.map((documentId) => (
            <span className="context-chip document-chip" key={`doc-${documentId}`}>
              <FileCode2 size={13} />
              <span>{documentLabel(documentId, documents)}</span>
              {mentionedDocumentIds.includes(documentId) && <em>@</em>}
              <button
                type="button"
                aria-label={`移除 ${documentLabel(documentId, documents)} 引用`}
                onClick={() => onRemoveDocumentReference(documentId)}
              ><X size={12} /></button>
            </span>
          ))}
          {mentionedOnlyIds.map((documentId) => (
            <span className="context-chip document-chip is-auto" key={`mentioned-${documentId}`}>
              <FileCode2 size={13} />
              <span>@{documentLabel(documentId, documents)}</span>
              <em>自动</em>
            </span>
          ))}
          {selections.map((selection) => (
            <span className="context-chip selection-chip" key={selection.id}>
              <span className="selection-chip-bar" />
              <span>{shortText(selection.originalText)}</span>
              <button
                type="button"
                aria-label="移除选区引用"
                onClick={() => onRemoveSelection(selection.id)}
              ><X size={12} /></button>
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

function MessageBubble({
  message,
  documents,
  operationBusyKey,
  onApplyOperation,
  onIgnoreOperation,
}: Pick<ChatPanelProps, 'documents' | 'operationBusyKey' | 'onApplyOperation' | 'onIgnoreOperation'> & { message: ChatMessage }) {
  const operationStatuses = message.operationStatuses ?? {}
  const operationErrors = message.operationErrors ?? {}

  return (
    <article className={`message-row ${message.role === 'user' ? 'is-user' : 'is-assistant'}${message.failed ? ' is-failed' : ''}`}>
      <div className="message-avatar" aria-hidden="true">
        {message.role === 'user' ? <span>你</span> : <Bot size={16} />}
      </div>
      <div className="message-content-wrap">
        <div className="message-meta">
          <strong>{message.role === 'user' ? '你' : 'Markdown Agent'}</strong>
          {message.failed && <span className="message-failed-label">发送失败，可重试</span>}
        </div>
        <div className="message-bubble">
          {message.role === 'assistant'
            ? <MarkdownPreview content={message.content} compact />
            : <p className="user-message-text">{message.content}</p>}
        </div>
        {message.references && (message.references.documentIds.length > 0 || message.references.selections.length > 0) && (
          <div className="message-reference-summary">
            {message.references.documentIds.map((documentId) => (
              <span key={documentId}><FileCode2 size={11} />{documentLabel(documentId, documents)}</span>
            ))}
            {message.references.selections.map((selection) => (
              <span key={selection.id}><Paperclip size={11} />{shortText(selection.originalText, 18)}</span>
            ))}
          </div>
        )}
        {message.role === 'assistant' && message.operations && message.operations.length > 0 && (
          <div className="operation-list">
            <div className="operation-list-heading"><Sparkles size={14} />可应用的修改</div>
            {message.operations.map((operation) => {
              const status: OperationStatus = operationStatuses[operation.opId] ?? 'pending'
              return (
                <OperationCard
                  key={operation.opId}
                  operation={operation}
                  documentName={documentLabel(operation.documentId, documents)}
                  status={status}
                  error={operationErrors[operation.opId]}
                  busy={operationBusyKey === `${message.id}:${operation.opId}`}
                  onApply={() => onApplyOperation(message.id, operation)}
                  onIgnore={() => onIgnoreOperation(message.id, operation.opId)}
                />
              )
            })}
          </div>
        )}
      </div>
    </article>
  )
}

function ChatPanel({
  messages,
  input,
  sending,
  error,
  documents,
  referencedDocumentIds,
  mentionedDocumentIds,
  selections,
  operationBusyKey,
  onInputChange,
  onSend,
  onRemoveDocumentReference,
  onRemoveSelection,
  onApplyOperation,
  onIgnoreOperation,
}: ChatPanelProps) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const mentionedNames = useMemo(
    () => mentionedDocumentIds.map((id) => documentLabel(id, documents)),
    [mentionedDocumentIds, documents],
  )

  useEffect(() => {
    const container = scrollRef.current
    if (!container) return
    container.scrollTo({ top: container.scrollHeight, behavior: sending ? 'smooth' : 'auto' })
  }, [messages, sending])

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      onSend()
    }
  }

  return (
    <section className="chat-panel panel-shell">
      <header className="chat-header">
        <div className="chat-title-group">
          <div className="assistant-icon"><Sparkles size={17} /></div>
          <div>
            <div className="chat-title-line"><h2>Markdown Agent</h2><span className="online-pill"><span />在线</span></div>
            <p>读懂你的文档，陪你一起打磨内容</p>
          </div>
        </div>
        <button className="icon-button" type="button" aria-label="聊天选项" title="聊天选项"><ChevronDown size={17} /></button>
      </header>

      <div ref={scrollRef} className="chat-messages" aria-live="polite">
        {messages.length === 0 && !sending && (
          <div className="chat-empty">
            <div className="chat-empty-orbit"><Sparkles size={22} /></div>
            <p className="chat-empty-kicker">READY WHEN YOU ARE</p>
            <h3>从一个问题开始</h3>
            <p>让 AI 总结、解释、润色你的 Markdown，或直接提出修改建议。</p>
            <div className="prompt-suggestions">
              <span>“帮我梳理这篇文档的结构”</span>
              <span>“找出这段内容中的问题”</span>
            </div>
          </div>
        )}
        {messages.map((message) => (
          <MessageBubble
            key={message.id}
            message={message}
            documents={documents}
            operationBusyKey={operationBusyKey}
            onApplyOperation={onApplyOperation}
            onIgnoreOperation={onIgnoreOperation}
          />
        ))}
        {sending && (
          <div className="message-row is-assistant thinking-row">
            <div className="message-avatar" aria-hidden="true"><Bot size={16} /></div>
            <div className="message-content-wrap">
              <div className="message-meta"><strong>Markdown Agent</strong></div>
              <div className="thinking-bubble"><span className="thinking-dots"><i /><i /><i /></span><span>正在思考…</span></div>
            </div>
          </div>
        )}
      </div>

      {error && <div className="chat-error" role="alert"><span>{error}</span><span className="chat-error-hint">输入和引用已保留</span></div>}

      <ContextShelf
        documents={documents}
        referencedDocumentIds={referencedDocumentIds}
        mentionedDocumentIds={mentionedDocumentIds}
        selections={selections}
        onRemoveDocumentReference={onRemoveDocumentReference}
        onRemoveSelection={onRemoveSelection}
      />

      {mentionedNames.length > 0 && <p className="mention-hint">已识别 @ 引用：{mentionedNames.join('、')}</p>}

      <div className="chat-composer">
        <textarea
          value={input}
          onChange={(event) => onInputChange(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="问问你的 Markdown…"
          rows={3}
          disabled={sending}
          aria-label="聊天输入框"
        />
        <div className="composer-footer">
          <span className="composer-tip"><span className="composer-key">Enter</span> 发送 · <span className="composer-key">Shift Enter</span> 换行</span>
          <button
            className="send-button"
            type="button"
            disabled={sending || !input.trim()}
            onClick={onSend}
            aria-label="发送消息"
          >
            {sending ? <LoaderCircle className="spin" size={16} /> : <Send size={16} />}
          </button>
        </div>
      </div>
      <div className="chat-footer-note"><MessageCircle size={12} />同步请求 · 对话历史由后端保存</div>
    </section>
  )
}

export default ChatPanel
