import { useState } from 'react'
import { Copy, Check, Terminal, FileCode } from 'lucide-react'

export default function CodeBlock({ code, language = 'bash', title, className = '' }) {
  const [copied, setCopied] = useState(false)

  const handleCopy = () => {
    navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className={`rounded-2xl overflow-hidden bg-gray-900 dark:bg-gray-950 ${className}`}>
      {title && (
        <div className="flex items-center justify-between px-4 py-3 bg-gray-800 dark:bg-gray-900 border-b border-gray-700">
          <div className="flex items-center gap-2 text-gray-400">
            {language === 'bash' ? <Terminal size={16} /> : <FileCode size={16} />}
            <span className="text-sm font-medium">{title}</span>
          </div>
          <button
            onClick={handleCopy}
            className="p-1.5 hover:bg-gray-700 rounded-lg text-gray-400 hover:text-white transition-colors"
            title="Copy code"
          >
            {copied ? <Check size={16} className="text-green-400" /> : <Copy size={16} />}
          </button>
        </div>
      )}
      <pre className="p-4 overflow-x-auto text-sm">
        <code className="text-gray-300 font-mono">{code}</code>
      </pre>
    </div>
  )
}

export function InlineCode({ children }) {
  return (
    <code className="px-1.5 py-0.5 bg-gray-100 dark:bg-gray-800 rounded text-sm font-mono text-pink-600 dark:text-pink-400">
      {children}
    </code>
  )
}
