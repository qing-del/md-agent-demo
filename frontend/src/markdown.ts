import type { ChatOperation, LocalOperationResult, MarkdownHeading } from './types'

interface MarkdownLine {
  text: string
  start: number
  end: number
  after: number
}

function splitLines(source: string): MarkdownLine[] {
  if (!source) return []

  const lines: MarkdownLine[] = []
  let start = 0
  while (start < source.length) {
    const newlineIndex = source.indexOf('\n', start)
    const rawEnd = newlineIndex === -1 ? source.length : newlineIndex
    const hasCr = rawEnd > start && source[rawEnd - 1] === '\r'
    const end = hasCr ? rawEnd - 1 : rawEnd
    lines.push({
      text: source.slice(start, end),
      start,
      end,
      after: newlineIndex === -1 ? source.length : newlineIndex + 1,
    })
    if (newlineIndex === -1) break
    start = newlineIndex + 1
  }

  if (source.endsWith('\n')) {
    lines.push({ text: '', start: source.length, end: source.length, after: source.length })
  }
  return lines
}

function normalizeTitle(title: string): string {
  return title.trim().replace(/[ \t]+#+[ \t]*$/, '').trim().replace(/[ \t]+/g, ' ')
}

function headingFromLine(line: MarkdownLine): MarkdownHeading | null {
  const match = /^( {0,3})(#{1,6})(?:[ \t]+(.*)|[ \t]*)$/.exec(line.text)
  if (!match) return null
  const level = match[2].length
  const title = normalizeTitle(match[3] ?? '')
  if (!title) return null
  return {
    level,
    title,
    label: `${'#'.repeat(level)} ${title}`,
    start: line.start,
    end: line.end,
    after: line.after,
  }
}

export function parseMarkdownHeadings(source: string): MarkdownHeading[] {
  const headings: MarkdownHeading[] = []
  let inFence = false
  let fenceMarker = ''

  for (const line of splitLines(source)) {
    const fenceMatch = /^( {0,3})(`{3,}|~{3,})/.exec(line.text)
    if (fenceMatch) {
      const marker = fenceMatch[2][0]
      if (!inFence) {
        inFence = true
        fenceMarker = marker
      } else if (fenceMarker === marker) {
        inFence = false
        fenceMarker = ''
      }
      continue
    }
    if (!inFence) {
      const heading = headingFromLine(line)
      if (heading) headings.push(heading)
    }
  }
  return headings
}

function headingPathAt(headings: MarkdownHeading[], position: number): MarkdownHeading[] {
  const stack: Array<MarkdownHeading | undefined> = []
  for (const heading of headings) {
    if (heading.start > position) break
    stack.splice(heading.level - 1)
    stack[heading.level - 1] = heading
  }
  return stack.filter((heading): heading is MarkdownHeading => Boolean(heading))
}

export function sectionTextAtPosition(source: string, position: number): string {
  const headings = parseMarkdownHeadings(source)
  const path = headingPathAt(headings, Math.max(0, Math.min(position, source.length)))
  return path.length ? path.map((heading) => heading.label).join(' | ') : '正文'
}

function normalizePathPart(part: string): string {
  const match = /^(#{1,6})[ \t]*(.*)$/.exec(part.trim())
  if (!match) return part.trim().replace(/[ \t]+/g, ' ')
  const title = normalizeTitle(match[2])
  return `${match[1]} ${title}`.trim()
}

function sectionPathForHeading(headings: MarkdownHeading[], target: MarkdownHeading): string[] {
  const stack: Array<MarkdownHeading | undefined> = []
  for (const heading of headings) {
    if (heading.start > target.start) break
    stack.splice(heading.level - 1)
    stack[heading.level - 1] = heading
  }
  return stack.filter((heading): heading is MarkdownHeading => Boolean(heading)).map((heading) => heading.label)
}

function directBodyEnd(source: string, headings: MarkdownHeading[], target: MarkdownHeading): number {
  const nextHeading = headings.find((heading) => heading.start > target.start)
  if (!nextHeading) return source.length
  return nextHeading.start
}

function countMatches(source: string, needle: string): { count: number; first: number } {
  let count = 0
  let first = -1
  let cursor = 0
  while (cursor <= source.length - needle.length) {
    const index = source.indexOf(needle, cursor)
    if (index < 0) break
    if (first < 0) first = index
    count += 1
    cursor = index + Math.max(needle.length, 1)
  }
  return { count, first }
}

function uniqueMatchInSection(source: string, headings: MarkdownHeading[], target: MarkdownHeading, originalText: string): number | null {
  const bodyStart = target.after
  const bodyEnd = directBodyEnd(source, headings, target)
  const directBody = source.slice(bodyStart, bodyEnd)
  const matches = countMatches(directBody, originalText)
  if (matches.count === 0) return null
  if (matches.count > 1) throw new Error('原文在章节直属正文中出现多次，已阻止替换')
  return bodyStart + matches.first
}

function normalizedSectionPath(sectionText: string): string[] {
  return sectionText.split('|').map(normalizePathPart).filter(Boolean)
}

function samePath(left: string[], right: string[]): boolean {
  return left.length === right.length && left.every((part, index) => part === right[index])
}

function replaceAt(source: string, start: number, originalText: string, newText: string): LocalOperationResult {
  return {
    content: `${source.slice(0, start)}${newText}${source.slice(start + originalText.length)}`,
  }
}

export function applyLocalOperation(
  source: string,
  operation: ChatOperation,
  fallbackSectionTexts: string[] = [],
): LocalOperationResult {
  const headings = parseMarkdownHeadings(source)
  const normalizedPath = normalizedSectionPath(operation.sectionText)

  if (!normalizedPath.length) {
    throw new Error('提案缺少有效章节路径')
  }

  if (normalizedPath.length === 1 && normalizedPath[0] === '正文') {
    const matches = countMatches(source, operation.originalText)
    if (matches.count === 0) throw new Error('在正文中找不到原文')
    if (matches.count > 1) throw new Error('原文在正文中出现多次，已阻止替换')
    return replaceAt(source, matches.first, operation.originalText, operation.newText)
  }

  const candidates = headings.filter((heading) => {
    return samePath(sectionPathForHeading(headings, heading).map(normalizePathPart), normalizedPath)
  })

  const primaryError = candidates.length === 0
    ? `找不到章节“${operation.sectionText}”`
    : '在章节直属正文中找不到原文'
  if (candidates.length > 1) throw new Error(`章节“${operation.sectionText}”匹配到多个位置，已阻止替换`)

  if (candidates.length === 1) {
    const matchStart = uniqueMatchInSection(source, headings, candidates[0], operation.originalText)
    if (matchStart !== null) return replaceAt(source, matchStart, operation.originalText, operation.newText)
  }

  const fallbackPaths = [...new Set(fallbackSectionTexts.map(normalizedSectionPath)
    .filter((path) => path.length > 0)
    .map((path) => path.join(' | ')))]
    .map((path) => path.split(' | '))
  if (fallbackPaths.length === 0) throw new Error(primaryError)

  const fallbackCandidates = headings.filter((heading) => {
    const path = sectionPathForHeading(headings, heading).map(normalizePathPart)
    return fallbackPaths.some((fallbackPath) => samePath(path, fallbackPath))
  })
  if (fallbackCandidates.length > 1) {
    throw new Error('当前选区章节匹配到多个位置，已阻止替换')
  }
  if (fallbackCandidates.length === 1) {
    const matchStart = uniqueMatchInSection(source, headings, fallbackCandidates[0], operation.originalText)
    if (matchStart !== null) {
      return {
        ...replaceAt(source, matchStart, operation.originalText, operation.newText),
        usedSelectionFallback: true,
      }
    }
  }

  throw new Error(primaryError)
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes < 10 * 1024 ? 1 : 0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function byteLength(value: string): number {
  return new TextEncoder().encode(value).length
}
