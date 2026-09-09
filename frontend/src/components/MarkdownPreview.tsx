import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

interface MarkdownPreviewProps {
  content: string
  compact?: boolean
}

function MarkdownPreview({ content, compact = false }: MarkdownPreviewProps) {
  return (
    <div className={`markdown-preview${compact ? ' markdown-preview-compact' : ''}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ children, ...props }) => (
            <a {...props} target="_blank" rel="noreferrer">
              {children}
            </a>
          ),
        }}
      >
        {content || '暂无内容'}
      </ReactMarkdown>
    </div>
  )
}

export default MarkdownPreview
