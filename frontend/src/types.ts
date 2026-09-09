export type SyncStatus = 'synced' | 'dirty' | 'saving' | 'error'

export interface DocumentSummary {
  id: number
  fileName: string
  fileSizeBytes: number
  createdAt: string
  updatedAt: string
}

export interface DocumentDetail extends DocumentSummary {
  content: string
}

export interface DraftDocument extends DocumentSummary {
  content: string
  savedContent: string
  syncStatus: SyncStatus
  syncError?: string
}

export interface DocumentSyncResponse {
  documentId: number
  status: string
  updatedAt: string
}

export interface ChatSessionSummary {
  sessionKey: string
  title: string | null
  createdAt: string
  updatedAt: string
}

export interface HistoryMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface ChatSessionDetail {
  sessionKey: string
  title: string | null
  messages: HistoryMessage[]
  createdAt: string
  updatedAt: string
}

export interface SelectionContext {
  id: string
  documentId: number
  originalText: string
  sectionText: string
}

export interface ChatOperation {
  opId: string
  documentId: number
  sectionText: string
  originalText: string
  newText: string
}

export type OperationStatus = 'pending' | 'applied' | 'ignored'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  references?: {
    documentIds: number[]
    selections: SelectionContext[]
  }
  operations?: ChatOperation[]
  operationStatuses?: Record<string, OperationStatus>
  operationErrors?: Record<string, string>
  failed?: boolean
}

export interface ChatRequest {
  sessionKey: string
  message: {
    content: string
    documentIds: number[]
    selections: Array<{
      documentId: number
      originalText: string
      sectionText: string
    }>
  }
}

export interface ChatResponse {
  content: string
  operations?: ChatOperation[]
}

export interface MarkdownHeading {
  level: number
  title: string
  label: string
  start: number
  end: number
  after: number
}

export interface LocalOperationResult {
  content: string
}
