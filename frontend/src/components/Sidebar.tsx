import { useRef } from 'react'
import {
  FileCode2,
  FileText,
  LoaderCircle,
  MessageCircle,
  MessageSquarePlus,
  Plus,
  Search,
  Sparkles,
  Trash2,
  Upload,
  X,
} from 'lucide-react'
import type { ChatSessionSummary, DocumentSummary } from '../types'

interface SidebarProps {
  sessions: ChatSessionSummary[]
  documents: DocumentSummary[]
  activeSessionKey: string
  activeDocumentId: number | null
  referencedDocumentIds: number[]
  sessionSearch: string
  sessionsLoading: boolean
  sessionsError: string | null
  documentsLoading: boolean
  documentsError: string | null
  uploading: boolean
  deletingDocumentId: number | null
  onNewSession: () => void
  onSessionSearchChange: (value: string) => void
  onSelectSession: (sessionKey: string) => void
  onOpenDocument: (documentId: number) => void
  onToggleDocumentReference: (documentId: number) => void
  onDeleteDocument: (document: DocumentSummary) => void
  onUpload: (file: File) => void
  onRetryDocuments: () => void
  onRetrySessions: () => void
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '刚刚'
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(date)
}

function sessionLabel(session: ChatSessionSummary): string {
  return session.title?.trim() || '未命名会话'
}

function Sidebar({
  sessions,
  documents,
  activeSessionKey,
  activeDocumentId,
  referencedDocumentIds,
  sessionSearch,
  sessionsLoading,
  sessionsError,
  documentsLoading,
  documentsError,
  uploading,
  deletingDocumentId,
  onNewSession,
  onSessionSearchChange,
  onSelectSession,
  onOpenDocument,
  onToggleDocumentReference,
  onDeleteDocument,
  onUpload,
  onRetryDocuments,
  onRetrySessions,
}: SidebarProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (file) onUpload(file)
  }

  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div className="brand-lockup">
          <div className="brand-mark" aria-hidden="true">
            <FileText size={20} strokeWidth={2.5} />
            <Sparkles size={11} className="brand-spark" />
          </div>
          <div>
            <p className="brand-name">Markdown Agent</p>
            <p className="brand-caption">文档协作工作台</p>
          </div>
        </div>
        <button className="icon-button sidebar-close" type="button" aria-label="关闭侧栏">
          <X size={17} />
        </button>
      </div>

      <div className="sidebar-scroll">
        <button className="new-session-button" type="button" onClick={onNewSession}>
          <Plus size={17} strokeWidth={2.4} />
          新建会话
        </button>

        <section className="sidebar-section session-section" aria-labelledby="session-heading">
          <div className="sidebar-section-heading">
            <h2 id="session-heading">最近会话</h2>
            <span className="section-count">{sessions.length || ''}</span>
          </div>
          <label className="search-field">
            <Search size={15} aria-hidden="true" />
            <input
              type="search"
              value={sessionSearch}
              onChange={(event) => onSessionSearchChange(event.target.value)}
              placeholder="搜索会话"
              aria-label="搜索会话"
            />
            {sessionSearch && (
              <button
                className="clear-search"
                type="button"
                aria-label="清空会话搜索"
                onClick={() => onSessionSearchChange('')}
              >
                <X size={13} />
              </button>
            )}
          </label>

          {sessionsLoading && (
            <div className="list-loading" aria-label="会话加载中">
              <LoaderCircle className="spin" size={17} />
              <span>加载会话中…</span>
            </div>
          )}
          {!sessionsLoading && sessionsError && (
            <div className="inline-error">
              <span>{sessionsError}</span>
              <button type="button" onClick={onRetrySessions}>重试</button>
            </div>
          )}
          {!sessionsLoading && !sessionsError && sessions.length === 0 && (
            <div className="empty-list small-empty">
              <MessageCircle size={17} />
              <span>{sessionSearch ? '没有匹配的会话' : '还没有历史会话'}</span>
            </div>
          )}
          {!sessionsLoading && !sessionsError && sessions.length > 0 && (
            <div className="session-list">
              {sessions.map((session) => (
                <button
                  className={`session-item${session.sessionKey === activeSessionKey ? ' is-active' : ''}`}
                  type="button"
                  key={session.sessionKey}
                  onClick={() => onSelectSession(session.sessionKey)}
                >
                  <span className="session-icon"><MessageCircle size={15} /></span>
                  <span className="session-copy">
                    <span className="session-title">{sessionLabel(session)}</span>
                    <span className="session-date">{formatDate(session.updatedAt)}</span>
                  </span>
                </button>
              ))}
            </div>
          )}
        </section>

        <section className="sidebar-section document-section" aria-labelledby="document-heading">
          <div className="sidebar-section-heading">
            <div className="heading-with-count">
              <h2 id="document-heading">我的文档</h2>
              <span className="section-count">{documents.length || ''}</span>
            </div>
            <button
              className="section-action"
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              title="上传 Markdown 文档"
            >
              {uploading ? <LoaderCircle className="spin" size={15} /> : <Upload size={15} />}
              <span>{uploading ? '上传中' : '上传'}</span>
            </button>
            <input
              ref={fileInputRef}
              className="visually-hidden"
              type="file"
              accept=".md,.markdown,text/markdown"
              onChange={handleFileChange}
            />
          </div>

          {documentsLoading && (
            <div className="list-loading" aria-label="文档加载中">
              <LoaderCircle className="spin" size={17} />
              <span>加载文档中…</span>
            </div>
          )}
          {!documentsLoading && documentsError && (
            <div className="inline-error">
              <span>{documentsError}</span>
              <button type="button" onClick={onRetryDocuments}>重试</button>
            </div>
          )}
          {!documentsLoading && !documentsError && documents.length === 0 && (
            <div className="empty-list">
              <FileCode2 size={22} />
              <strong>从一篇 Markdown 开始</strong>
              <span>支持 .md 和 .markdown 文件</span>
            </div>
          )}
          {!documentsLoading && !documentsError && documents.length > 0 && (
            <div className="document-list">
              {documents.map((document) => {
                const isReferenced = referencedDocumentIds.includes(document.id)
                return (
                  <div
                    className={`document-item${document.id === activeDocumentId ? ' is-active' : ''}`}
                    key={document.id}
                  >
                    <button
                      className="document-main"
                      type="button"
                      onClick={() => onOpenDocument(document.id)}
                    >
                      <span className="document-icon"><FileCode2 size={16} /></span>
                      <span className="document-copy">
                        <span className="document-name" title={document.fileName}>{document.fileName}</span>
                        <span className="document-meta">{formatDate(document.updatedAt)}</span>
                      </span>
                    </button>
                    <div className="document-actions">
                      <button
                        className={`document-action${isReferenced ? ' is-referenced' : ''}`}
                        type="button"
                        aria-label={isReferenced ? `取消引用 ${document.fileName}` : `引用 ${document.fileName}`}
                        title={isReferenced ? '取消引用' : '加入聊天引用'}
                        onClick={() => onToggleDocumentReference(document.id)}
                      >
                        <MessageSquarePlus size={15} />
                      </button>
                      <button
                        className="document-action danger-action"
                        type="button"
                        aria-label={`删除 ${document.fileName}`}
                        title="删除文档"
                        disabled={deletingDocumentId === document.id}
                        onClick={() => onDeleteDocument(document)}
                      >
                        {deletingDocumentId === document.id
                          ? <LoaderCircle className="spin" size={15} />
                          : <Trash2 size={14} />}
                      </button>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </section>
      </div>

      <div className="sidebar-footer">
        <span className="status-dot" aria-hidden="true" />
        <span>本地开发环境</span>
        <span className="footer-url">localhost:8080</span>
      </div>
    </aside>
  )
}

export default Sidebar
