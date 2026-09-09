import type {
  ChatRequest,
  ChatResponse,
  ChatSessionDetail,
  ChatSessionSummary,
  DocumentDetail,
  DocumentSummary,
  DocumentSyncResponse,
} from './types'

const apiBase = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function parseError(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: string; error?: string }
    if (body.message) return body.message
    if (body.error) return body.error
  } catch {
    // Some proxy failures return an empty or non-JSON body.
  }
  return response.statusText || `请求失败（${response.status}）`
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    throw new ApiError(response.status, await parseError(response))
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const documentApi = {
  list: () => request<DocumentSummary[]>('/api/documents'),
  get: (documentId: number) => request<DocumentDetail>(`/api/documents/${documentId}`),
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<DocumentDetail>('/api/documents', { method: 'POST', body: form })
  },
  saveDraft: (documentId: number, content: string) =>
    request<DocumentSyncResponse>(`/api/documents/${documentId}`, {
      method: 'PUT',
      body: JSON.stringify({ content }),
    }),
  remove: (documentId: number) =>
    request<void>(`/api/documents/${documentId}`, { method: 'DELETE' }),
}

export const sessionApi = {
  list: (title?: string) => {
    const query = title?.trim() ? `?title=${encodeURIComponent(title.trim())}` : ''
    return request<ChatSessionSummary[]>(`/api/sessions${query}`)
  },
  get: (sessionKey: string) => request<ChatSessionDetail>(`/api/sessions/${sessionKey}`),
}

export const chatApi = {
  send: (payload: ChatRequest) =>
    request<ChatResponse>('/api/chat', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
}
