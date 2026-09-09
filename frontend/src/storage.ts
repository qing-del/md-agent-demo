const SESSION_STORAGE_KEY = 'markdown-agent.current-session'
const RECENT_DOCUMENT_STORAGE_KEY = 'markdown-agent.recent-document'

export function createSessionKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function isStandardUuid(value: string | null): value is string {
  return value !== null &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
}

export function getStoredSessionKey(): string | null {
  try {
    const value = localStorage.getItem(SESSION_STORAGE_KEY)
    return isStandardUuid(value) ? value : null
  } catch {
    return null
  }
}

export function storeSessionKey(sessionKey: string): void {
  try {
    localStorage.setItem(SESSION_STORAGE_KEY, sessionKey)
  } catch {
    // localStorage may be unavailable in private browsing or embedded previews.
  }
}

export function getStoredRecentDocumentId(): number | null {
  try {
    const value = localStorage.getItem(RECENT_DOCUMENT_STORAGE_KEY)
    if (!value) return null
    const id = Number(value)
    return Number.isInteger(id) && id > 0 ? id : null
  } catch {
    return null
  }
}

export function storeRecentDocumentId(documentId: number | null): void {
  try {
    if (documentId === null) {
      localStorage.removeItem(RECENT_DOCUMENT_STORAGE_KEY)
    } else {
      localStorage.setItem(RECENT_DOCUMENT_STORAGE_KEY, String(documentId))
    }
  } catch {
    // Keep the editor usable if localStorage is blocked.
  }
}
