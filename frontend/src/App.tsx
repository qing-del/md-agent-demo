import { FileText, Sparkles } from 'lucide-react'

function App() {
  return (
    <main className="app-shell scaffold-shell">
      <div className="scaffold-card">
        <div className="brand-mark" aria-hidden="true">
          <FileText size={22} strokeWidth={2.4} />
          <Sparkles size={13} className="brand-spark" />
        </div>
        <p className="eyebrow">MARKDOWN AGENT</p>
        <h1>你的文档工作台正在加载</h1>
        <p className="muted-copy">前端骨架已就绪，下一步接入文档、会话与 AI 协作能力。</p>
      </div>
    </main>
  )
}

export default App
