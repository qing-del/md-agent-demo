import { Check, CheckCircle2, CircleSlash2, LoaderCircle, RotateCcw, X } from 'lucide-react'
import type { ChatOperation, OperationStatus } from '../types'

interface OperationCardProps {
  operation: ChatOperation
  documentName: string
  status: OperationStatus
  error?: string
  busy: boolean
  onApply: () => void
  onIgnore: () => void
}

function OperationCard({
  operation,
  documentName,
  status,
  error,
  busy,
  onApply,
  onIgnore,
}: OperationCardProps) {
  const isApplied = status === 'applied'
  const isIgnored = status === 'ignored'

  return (
    <article className={`operation-card${isApplied ? ' is-applied' : ''}${isIgnored ? ' is-ignored' : ''}`}>
      <div className="operation-card-header">
        <div className="operation-heading">
          <span className="operation-icon">
            {isApplied ? <CheckCircle2 size={16} /> : isIgnored ? <CircleSlash2 size={16} /> : <RotateCcw size={15} />}
          </span>
          <div>
            <p className="operation-eyebrow">替换提案</p>
            <h3>{documentName}</h3>
          </div>
        </div>
        <span className={`operation-status ${isApplied ? 'is-applied' : isIgnored ? 'is-ignored' : ''}`}>
          {isApplied ? '已应用到草稿' : isIgnored ? '已忽略' : '待处理'}
        </span>
      </div>

      <div className="operation-section"><span>章节</span><code>{operation.sectionText}</code></div>

      <div className="operation-diff">
        <div className="diff-block diff-original">
          <span className="diff-label"><span className="diff-dot" />原文</span>
          <pre>{operation.originalText}</pre>
        </div>
        <div className="diff-arrow" aria-hidden="true">→</div>
        <div className="diff-block diff-new">
          <span className="diff-label"><span className="diff-dot" />新文本</span>
          <pre>{operation.newText}</pre>
        </div>
      </div>

      {error && <p className="operation-error" role="alert">{error}</p>}

      {!isApplied && !isIgnored && (
        <div className="operation-actions">
          <button className="operation-apply" type="button" disabled={busy} onClick={onApply}>
            {busy ? <LoaderCircle className="spin" size={14} /> : <Check size={14} />}
            {busy ? '正在定位…' : '应用到草稿'}
          </button>
          <button className="operation-ignore" type="button" disabled={busy} onClick={onIgnore}>
            <X size={14} />忽略
          </button>
        </div>
      )}
    </article>
  )
}

export default OperationCard
