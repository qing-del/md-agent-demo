import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { AlertCircle, CheckCircle2, Info, LoaderCircle, Menu, X } from 'lucide-react'
import { ApiError, chatApi, documentApi, sessionApi } from './api'
import ChatPanel from './components/ChatPanel'
import EditorPanel from './components/EditorPanel'
import Sidebar from './components/Sidebar'
import { applyLocalOperation, byteLength } from './markdown'
import {
  createSessionKey,
  getStoredRecentDocumentId,
  getStoredSessionKey,
  storeRecentDocumentId,
  storeSessionKey,
} from './storage'
import type {
  ChatMessage,
  ChatOperation,
  ChatSessionSummary,
  DocumentDetail,
  DocumentSummary,
  DraftDocument,
  SelectionContext,
} from './types'

type ToastKind = 'success' | 'error' | 'info'

interface ToastState {
  kind: ToastKind
  message: string
}

function makeId(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

function makeDraft(detail: DocumentDetail): DraftDocument {
  return {
    ...detail,
    content: detail.content ?? '',
    savedContent: detail.content ?? '',
    syncStatus: 'synced',
  }
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 404) return '资源不存在，可能已被删除。'
    if (error.status === 400) return error.message || '请求参数不正确。'
    return error.message || `服务暂时不可用（${error.status}）`
  }
  if (error instanceof Error && error.message) return error.message
  return '网络连接失败，请检查后端服务后重试。'
}

function uniqueNumbers(values: number[]): number[] {
  return [...new Set(values)]
}

function isMarkdownFile(fileName: string): boolean {
  return /\.(md|markdown)$/i.test(fileName)
}

function mentionMatches(input: string, candidate: string): boolean {
  const mention = `@${candidate}`
  let start = input.indexOf(mention)
  while (start >= 0) {
    const boundary = input[start + mention.length]
    if (!boundary || /[\s，。！？!?、,.;:：）》）】\]}]/.test(boundary)) return true
    start = input.indexOf(mention, start + mention.length)
  }
  return false
}

function mentionedDocumentIds(input: string, documents: DocumentSummary[]): number[] {
  return documents
    .filter((document) => {
      const fileName = document.fileName
      const stem = fileName.replace(/\.(md|markdown)$/i, '')
      return mentionMatches(input, fileName) || (stem !== fileName && mentionMatches(input, stem))
    })
    .map((document) => document.id)
}

function App() {
  const [documents, setDocuments] = useState<DocumentSummary[]>([])
  const [drafts, setDrafts] = useState<Record<number, DraftDocument>>({})
  const [activeDocumentId, setActiveDocumentId] = useState<number | null>(null)
  const [documentsLoading, setDocumentsLoading] = useState(true)
  const [documentsError, setDocumentsError] = useState<string | null>(null)
  const [documentLoading, setDocumentLoading] = useState(false)
  const [documentError, setDocumentError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [deletingDocumentId, setDeletingDocumentId] = useState<number | null>(null)

  const [sessions, setSessions] = useState<ChatSessionSummary[]>([])
  const [sessionSearch, setSessionSearch] = useState('')
  const [sessionsLoading, setSessionsLoading] = useState(true)
  const [sessionsError, setSessionsError] = useState<string | null>(null)
  const [sessionKey, setSessionKey] = useState(() => getStoredSessionKey() ?? createSessionKey())
  const [sessionLoading, setSessionLoading] = useState(false)

  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [chatInput, setChatInput] = useState('')
  const [chatSending, setChatSending] = useState(false)
  const [chatError, setChatError] = useState<string | null>(null)
  const [referencedDocumentIds, setReferencedDocumentIds] = useState<number[]>([])
  const [selections, setSelections] = useState<SelectionContext[]>([])
  const [operationBusyKey, setOperationBusyKey] = useState<string | null>(null)
  const [toast, setToast] = useState<ToastState | null>(null)
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)

  const documentRequestRef = useRef(0)
  const sessionRequestRef = useRef(0)
  const initialHistoryAttemptedRef = useRef(false)
  const sessionSearchMountedRef = useRef(false)

  const activeDraft = activeDocumentId === null ? null : drafts[activeDocumentId] ?? null
  const activeMentionedIds = useMemo(
    () => mentionedDocumentIds(chatInput, documents),
    [chatInput, documents],
  )

  const showToast = useCallback((kind: ToastKind, message: string) => {
    setToast({ kind, message })
  }, [])

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(null), 4600)
    return () => window.clearTimeout(timer)
  }, [toast])

  const loadDocumentDetail = useCallback(async (documentId: number) => {
    const requestId = ++documentRequestRef.current
    setDocumentLoading(true)
    setDocumentError(null)
    try {
      const detail = await documentApi.get(documentId)
      if (requestId !== documentRequestRef.current) return
      setDrafts((current) => {
        const existing = current[documentId]
        if (existing && existing.content !== existing.savedContent) return current
        return { ...current, [documentId]: makeDraft(detail) }
      })
    } catch (error) {
      if (requestId === documentRequestRef.current) setDocumentError(errorMessage(error))
    } finally {
      if (requestId === documentRequestRef.current) setDocumentLoading(false)
    }
  }, [])

  const refreshDocuments = useCallback(async () => {
    setDocumentsLoading(true)
    setDocumentsError(null)
    try {
      const result = await documentApi.list()
      setDocuments(result)
      const preferredId = getStoredRecentDocumentId()
      const nextId = preferredId && result.some((document) => document.id === preferredId)
        ? preferredId
        : result[0]?.id ?? null
      if (activeDocumentId === null && nextId !== null) {
        setActiveDocumentId(nextId)
        storeRecentDocumentId(nextId)
        void loadDocumentDetail(nextId)
      }
    } catch (error) {
      setDocumentsError(errorMessage(error))
    } finally {
      setDocumentsLoading(false)
    }
  }, [activeDocumentId, loadDocumentDetail])

  useEffect(() => {
    void refreshDocuments()
  }, [refreshDocuments])

  const loadSessionHistory = useCallback(async (nextSessionKey: string) => {
    const requestId = ++sessionRequestRef.current
    setSessionKey(nextSessionKey)
    storeSessionKey(nextSessionKey)
    setSessionLoading(true)
    setSessionsError(null)
    setChatError(null)
    setMessages([])
    setReferencedDocumentIds([])
    setSelections([])
    try {
      const detail = await sessionApi.get(nextSessionKey)
      if (requestId !== sessionRequestRef.current) return
      setMessages(detail.messages.map((message, index) => ({
        id: `history-${nextSessionKey}-${index}`,
        role: message.role,
        content: message.content,
      })))
    } catch (error) {
      if (requestId === sessionRequestRef.current) setSessionsError(errorMessage(error))
    } finally {
      if (requestId === sessionRequestRef.current) setSessionLoading(false)
    }
  }, [])

  const refreshSessions = useCallback(async (title = '') => {
    setSessionsLoading(true)
    setSessionsError(null)
    try {
      const result = await sessionApi.list(title)
      setSessions(result)
      if (!initialHistoryAttemptedRef.current) {
        initialHistoryAttemptedRef.current = true
        const storedKey = getStoredSessionKey()
        if (storedKey && result.some((session) => session.sessionKey === storedKey)) {
          void loadSessionHistory(storedKey)
        }
      }
    } catch (error) {
      setSessionsError(errorMessage(error))
    } finally {
      setSessionsLoading(false)
    }
  }, [loadSessionHistory])

  useEffect(() => {
    void refreshSessions()
  }, [refreshSessions])

  useEffect(() => {
    if (!sessionSearchMountedRef.current) {
      sessionSearchMountedRef.current = true
      return
    }
    const timer = window.setTimeout(() => void refreshSessions(sessionSearch), 280)
    return () => window.clearTimeout(timer)
  }, [refreshSessions, sessionSearch])

  useEffect(() => {
    const warnBeforeLeaving = (event: BeforeUnloadEvent) => {
      const hasDirtyDraft = Object.values(drafts).some((draft) => draft.content !== draft.savedContent)
      if (!hasDirtyDraft) return
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', warnBeforeLeaving)
    return () => window.removeEventListener('beforeunload', warnBeforeLeaving)
  }, [drafts])

  const handleOpenDocument = useCallback((documentId: number) => {
    if (documentId === activeDocumentId) return
    if (activeDraft && activeDraft.content !== activeDraft.savedContent) {
      const proceed = window.confirm('当前文档有未保存修改，确定切换吗？草稿会保留在本页面中。')
      if (!proceed) return
    }
    setDocumentError(null)
    setActiveDocumentId(documentId)
    storeRecentDocumentId(documentId)
    setMobileSidebarOpen(false)
    if (drafts[documentId]) return
    void loadDocumentDetail(documentId)
  }, [activeDocumentId, activeDraft, drafts, loadDocumentDetail])

  const handleDraftChange = useCallback((content: string) => {
    if (!activeDocumentId) return
    setDrafts((current) => {
      const draft = current[activeDocumentId]
      if (!draft) return current
      const isDirty = content !== draft.savedContent
      return {
        ...current,
        [activeDocumentId]: {
          ...draft,
          content,
          fileSizeBytes: byteLength(content),
          syncStatus: isDirty ? 'dirty' : 'synced',
          syncError: undefined,
        },
      }
    })
  }, [activeDocumentId])

  const handleSaveDraft = useCallback(async () => {
    if (!activeDraft || activeDraft.syncStatus === 'saving') return
    if (activeDraft.content === activeDraft.savedContent && !activeDraft.syncError) return
    const documentId = activeDraft.id
    const content = activeDraft.content
    setDrafts((current) => ({
      ...current,
      [documentId]: { ...current[documentId], syncStatus: 'saving', syncError: undefined },
    }))
    try {
      const response = await documentApi.saveDraft(documentId, content)
      setDrafts((current) => ({
        ...current,
        [documentId]: {
          ...current[documentId],
          savedContent: content,
          content,
          fileSizeBytes: byteLength(content),
          updatedAt: response.updatedAt,
          syncStatus: 'synced',
          syncError: undefined,
        },
      }))
      setDocuments((current) => current.map((document) => document.id === documentId
        ? { ...document, fileSizeBytes: byteLength(content), updatedAt: response.updatedAt }
        : document))
      showToast('success', '草稿已同步到后端')
    } catch (error) {
      const message = errorMessage(error)
      setDrafts((current) => ({
        ...current,
        [documentId]: { ...current[documentId], syncStatus: 'error', syncError: message },
      }))
      showToast('error', `保存失败：${message}`)
    }
  }, [activeDraft, showToast])

  const handleUpload = useCallback(async (file: File) => {
    if (!isMarkdownFile(file.name)) {
      showToast('error', '只支持上传 .md 或 .markdown 文件')
      return
    }
    if (activeDraft && activeDraft.content !== activeDraft.savedContent) {
      const proceed = window.confirm('当前文档有未保存修改，上传后会打开新文档。确定继续吗？')
      if (!proceed) return
    }
    setUploading(true)
    try {
      const detail = await documentApi.upload(file)
      setDrafts((current) => ({ ...current, [detail.id]: makeDraft(detail) }))
      setActiveDocumentId(detail.id)
      storeRecentDocumentId(detail.id)
      setDocumentError(null)
      const refreshed = await documentApi.list()
      setDocuments(refreshed)
      setMobileSidebarOpen(false)
      showToast('success', `${detail.fileName} 已上传并打开`)
    } catch (error) {
      showToast('error', `上传失败：${errorMessage(error)}`)
    } finally {
      setUploading(false)
    }
  }, [activeDraft, showToast])

  const handleDeleteDocument = useCallback(async (document: DocumentSummary) => {
    const draft = drafts[document.id]
    if (draft && draft.content !== draft.savedContent) {
      const proceed = window.confirm('这篇文档有未保存修改，删除后将无法恢复。确定继续吗？')
      if (!proceed) return
    }
    if (!window.confirm(`确定删除“${document.fileName}”吗？此操作不可撤销。`)) return
    setDeletingDocumentId(document.id)
    try {
      await documentApi.remove(document.id)
      const remaining = documents.filter((item) => item.id !== document.id)
      setDocuments(remaining)
      setDrafts((current) => {
        const next = { ...current }
        delete next[document.id]
        return next
      })
      setReferencedDocumentIds((current) => current.filter((id) => id !== document.id))
      setSelections((current) => current.filter((selection) => selection.documentId !== document.id))
      if (activeDocumentId === document.id) {
        const nextId = remaining[0]?.id ?? null
        setActiveDocumentId(nextId)
        storeRecentDocumentId(nextId)
        setDocumentError(null)
        if (nextId !== null) void loadDocumentDetail(nextId)
      }
      showToast('success', `${document.fileName} 已删除`)
    } catch (error) {
      showToast('error', `删除失败：${errorMessage(error)}`)
    } finally {
      setDeletingDocumentId(null)
    }
  }, [activeDocumentId, documents, drafts, loadDocumentDetail, showToast])

  const handleToggleDocumentReference = useCallback((documentId: number) => {
    setReferencedDocumentIds((current) => current.includes(documentId)
      ? current.filter((id) => id !== documentId)
      : [...current, documentId])
  }, [])

  const handleAddSelection = useCallback((selection: Omit<SelectionContext, 'id'>) => {
    const duplicate = selections.some((item) => item.documentId === selection.documentId
      && item.originalText === selection.originalText
      && item.sectionText === selection.sectionText)
    if (duplicate) {
      showToast('info', '这段选区已经在聊天上下文中')
      return
    }
    setSelections((current) => [...current, { ...selection, id: makeId('selection') }])
    showToast('success', '选区已加入聊天上下文')
  }, [selections, showToast])

  const handleNewSession = useCallback(() => {
    if (chatSending) {
      showToast('info', '当前消息还在处理中，请稍候')
      return
    }
    const nextKey = createSessionKey()
    sessionRequestRef.current += 1
    setSessionKey(nextKey)
    storeSessionKey(nextKey)
    setMessages([])
    setChatInput('')
    setChatError(null)
    setReferencedDocumentIds([])
    setSelections([])
    setSessionLoading(false)
    setMobileSidebarOpen(false)
    showToast('success', '已创建新会话')
  }, [chatSending, showToast])

  const handleSelectSession = useCallback((nextKey: string) => {
    if (nextKey === sessionKey) return
    if (chatSending) {
      showToast('info', '当前消息还在处理中，请稍候再切换会话')
      return
    }
    setMobileSidebarOpen(false)
    void loadSessionHistory(nextKey)
  }, [chatSending, loadSessionHistory, sessionKey, showToast])

  const handleSend = useCallback(async () => {
    const content = chatInput.trim()
    if (!content || chatSending) return
    const autoIds = mentionedDocumentIds(content, documents)
    const documentIds = uniqueNumbers([...referencedDocumentIds, ...autoIds])
    const selectionPayload = selections.map(({ documentId, originalText, sectionText }) => ({
      documentId,
      originalText,
      sectionText,
    }))
    const userMessageId = makeId('user')
    const userMessage: ChatMessage = {
      id: userMessageId,
      role: 'user',
      content,
      failed: false,
      references: { documentIds, selections },
    }
    setMessages((current) => [...current, userMessage])
    setChatSending(true)
    setChatError(null)
    try {
      const response = await chatApi.send({
        sessionKey,
        message: { content, documentIds, selections: selectionPayload },
      })
      const operations = Array.isArray(response.operations) ? response.operations : []
      const statuses = operations.reduce<Record<string, 'pending'>>((result, operation) => {
        result[operation.opId] = 'pending'
        return result
      }, {})
      setMessages((current) => [...current, {
        id: makeId('assistant'),
        role: 'assistant',
        content: response.content || 'AI 没有返回可展示的内容。',
        operations,
        operationStatuses: statuses,
      }])
      setReferencedDocumentIds(documentIds)
      setChatInput('')
      void refreshSessions(sessionSearch)
    } catch (error) {
      const message = errorMessage(error)
      setMessages((current) => current.map((item) => item.id === userMessageId
        ? { ...item, failed: true }
        : item))
      setChatError(message)
      showToast('error', `发送失败：${message}`)
    } finally {
      setChatSending(false)
    }
  }, [chatInput, chatSending, documents, refreshSessions, referencedDocumentIds, selections, sessionKey, sessionSearch, showToast])

  const ensureDraft = useCallback(async (documentId: number): Promise<DraftDocument> => {
    const cached = drafts[documentId]
    if (cached) return cached
    const detail = await documentApi.get(documentId)
    const nextDraft = makeDraft(detail)
    setDrafts((current) => current[documentId] ? current : { ...current, [documentId]: nextDraft })
    return nextDraft
  }, [drafts])

  const handleApplyOperation = useCallback(async (messageId: string, operation: ChatOperation) => {
    const busyKey = `${messageId}:${operation.opId}`
    setOperationBusyKey(busyKey)
    try {
      const draft = await ensureDraft(operation.documentId)
      const result = applyLocalOperation(draft.content, operation)
      setDrafts((current) => {
        const existing = current[operation.documentId] ?? draft
        return {
          ...current,
          [operation.documentId]: {
            ...existing,
            content: result.content,
            fileSizeBytes: byteLength(result.content),
            syncStatus: result.content === existing.savedContent ? 'synced' : 'dirty',
            syncError: undefined,
          },
        }
      })
      setMessages((current) => current.map((message) => message.id === messageId
        ? (() => {
          const operationErrors = { ...message.operationErrors }
          delete operationErrors[operation.opId]
          return {
            ...message,
            operationStatuses: { ...message.operationStatuses, [operation.opId]: 'applied' },
            operationErrors,
          }
        })()
        : message))
      showToast('success', '提案已应用到本地草稿，请保存以同步')
    } catch (error) {
      const message = errorMessage(error)
      setMessages((current) => current.map((item) => item.id === messageId
        ? { ...item, operationErrors: { ...item.operationErrors, [operation.opId]: message } }
        : item))
      showToast('error', `无法应用提案：${message}`)
    } finally {
      setOperationBusyKey(null)
    }
  }, [ensureDraft, showToast])

  const handleIgnoreOperation = useCallback((messageId: string, opId: string) => {
    setMessages((current) => current.map((message) => message.id === messageId
      ? (() => {
        const operationErrors = { ...message.operationErrors }
        delete operationErrors[opId]
        return {
          ...message,
          operationStatuses: { ...message.operationStatuses, [opId]: 'ignored' },
          operationErrors,
        }
      })()
      : message))
  }, [])

  return (
    <main className="app-shell">
      <button
        className="mobile-menu-button"
        type="button"
        aria-label="打开导航"
        onClick={() => setMobileSidebarOpen(true)}
      >
        <Menu size={18} />
      </button>
      {mobileSidebarOpen && <button className="mobile-scrim" type="button" aria-label="关闭导航" onClick={() => setMobileSidebarOpen(false)} />}
      <div className={`workspace${mobileSidebarOpen ? ' sidebar-open' : ''}`}>
        <Sidebar
          sessions={sessions}
          documents={documents}
          activeSessionKey={sessionKey}
          activeDocumentId={activeDocumentId}
          referencedDocumentIds={referencedDocumentIds}
          sessionSearch={sessionSearch}
          sessionsLoading={sessionsLoading || sessionLoading}
          sessionsError={sessionsError}
          documentsLoading={documentsLoading}
          documentsError={documentsError}
          uploading={uploading}
          deletingDocumentId={deletingDocumentId}
          onNewSession={handleNewSession}
          onSessionSearchChange={setSessionSearch}
          onSelectSession={handleSelectSession}
          onOpenDocument={handleOpenDocument}
          onToggleDocumentReference={handleToggleDocumentReference}
          onDeleteDocument={handleDeleteDocument}
          onUpload={handleUpload}
          onRetryDocuments={() => void refreshDocuments()}
          onRetrySessions={() => void refreshSessions(sessionSearch)}
        />
        <EditorPanel
          document={activeDraft}
          loading={documentLoading}
          error={documentError}
          selectionCount={selections.length}
          onChange={handleDraftChange}
          onSave={handleSaveDraft}
          onRetry={() => activeDocumentId !== null && void loadDocumentDetail(activeDocumentId)}
          onAddSelection={handleAddSelection}
        />
        <ChatPanel
          messages={messages}
          input={chatInput}
          sending={chatSending}
          error={chatError}
          documents={documents}
          referencedDocumentIds={referencedDocumentIds}
          mentionedDocumentIds={activeMentionedIds}
          selections={selections}
          operationBusyKey={operationBusyKey}
          onInputChange={setChatInput}
          onSend={handleSend}
          onRemoveDocumentReference={(documentId) => setReferencedDocumentIds((current) => current.filter((id) => id !== documentId))}
          onRemoveSelection={(selectionId) => setSelections((current) => current.filter((selection) => selection.id !== selectionId))}
          onApplyOperation={handleApplyOperation}
          onIgnoreOperation={handleIgnoreOperation}
        />
      </div>

      {toast && (
        <div className={`toast toast-${toast.kind}`} role="status">
          {toast.kind === 'success' && <CheckCircle2 size={16} />}
          {toast.kind === 'error' && <AlertCircle size={16} />}
          {toast.kind === 'info' && <Info size={16} />}
          <span>{toast.message}</span>
          <button type="button" aria-label="关闭提示" onClick={() => setToast(null)}><X size={14} /></button>
        </div>
      )}
      {(documentLoading || sessionLoading) && <LoaderCircle className="global-loading-indicator spin" size={15} aria-label="加载中" />}
    </main>
  )
}

export default App
