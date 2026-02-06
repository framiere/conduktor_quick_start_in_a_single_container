import { useState } from 'react'
import { Copy, Check, Terminal, FileCode } from 'lucide-react'

function highlightYaml(code) {
  return code
    .split('\n')
    .map(line => {
      // Comments
      if (line.trim().startsWith('#')) {
        return `<span class="text-gray-500">${escapeHtml(line)}</span>`
      }
      // Key-value pairs
      return line.replace(
        /^(\s*)([a-zA-Z_][a-zA-Z0-9_-]*)(:)(\s*)(.*)$/,
        (match, indent, key, colon, space, value) => {
          let highlightedValue = escapeHtml(value)
          // Strings
          if (value.startsWith('"') || value.startsWith("'")) {
            highlightedValue = `<span class="text-green-400">${escapeHtml(value)}</span>`
          }
          // Numbers
          else if (/^\d+$/.test(value)) {
            highlightedValue = `<span class="text-orange-400">${escapeHtml(value)}</span>`
          }
          // Booleans
          else if (/^(true|false)$/i.test(value)) {
            highlightedValue = `<span class="text-purple-400">${escapeHtml(value)}</span>`
          }
          // List item prefix
          else if (value.startsWith('-')) {
            highlightedValue = `<span class="text-gray-400">-</span>${escapeHtml(value.slice(1))}`
          }
          return `${indent}<span class="text-cyan-400">${escapeHtml(key)}</span><span class="text-gray-400">${colon}</span>${space}${highlightedValue}`
        }
      )
    })
    .join('\n')
}

function highlightJava(code) {
  const keywords = new Set(['public', 'private', 'protected', 'class', 'interface', 'extends', 'implements', 'static', 'final', 'void', 'return', 'new', 'if', 'else', 'for', 'while', 'try', 'catch', 'throw', 'throws', 'import', 'package'])
  const types = new Set(['String', 'int', 'boolean', 'long', 'double', 'float', 'Map', 'List', 'Set', 'Optional', 'HttpsServer', 'ValidationResult', 'ConcurrentMap', 'CRDKind', 'Object'])

  // Single-pass tokenizer: each token matched once from raw code, then escaped
  const tokenRegex = /(\/\/.*$|\/\*[\s\S]*?\*\/|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\b[a-zA-Z_]\w*\b|\b\d+\b)/gm
  let result = ''
  let lastIndex = 0

  for (const match of code.matchAll(tokenRegex)) {
    result += escapeHtml(code.slice(lastIndex, match.index))
    const token = match[0]
    const escaped = escapeHtml(token)

    if (token.startsWith('//') || token.startsWith('/*')) {
      result += `<span class="text-gray-500">${escaped}</span>`
    } else if (token.startsWith('"') || token.startsWith("'")) {
      result += `<span class="text-green-400">${escaped}</span>`
    } else if (keywords.has(token)) {
      result += `<span class="text-purple-400">${escaped}</span>`
    } else if (types.has(token)) {
      result += `<span class="text-cyan-400">${escaped}</span>`
    } else if (/^\d+$/.test(token)) {
      result += `<span class="text-orange-400">${escaped}</span>`
    } else {
      result += escaped
    }

    lastIndex = match.index + match[0].length
  }

  result += escapeHtml(code.slice(lastIndex))
  return result
}

function highlightBash(code) {
  const commands = new Set(['kubectl', 'git', 'npm', 'mvn', 'grep', 'docker', 'make', 'conduktor'])

  return code
    .split('\n')
    .map(line => {
      if (line.trim().startsWith('#')) {
        return `<span class="text-gray-500">${escapeHtml(line)}</span>`
      }

      // Single-pass tokenizer on raw line
      const tokenRegex = /("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\$\{[^}]*\}|\$\w+|\|\||&&|\||[<>]|\s--?[a-zA-Z0-9-]+|\b[a-zA-Z_]\w*\b)/g
      let result = ''
      let lastIndex = 0
      let firstWord = true

      for (const match of line.matchAll(tokenRegex)) {
        result += escapeHtml(line.slice(lastIndex, match.index))
        const token = match[0]

        if (token.startsWith('"') || token.startsWith("'")) {
          result += `<span class="text-green-400">${escapeHtml(token)}</span>`
        } else if (token.startsWith('$')) {
          result += `<span class="text-yellow-400">${escapeHtml(token)}</span>`
        } else if (token === '|' || token === '||' || token === '&&' || token === '>' || token === '<') {
          result += `<span class="text-purple-400">${escapeHtml(token)}</span>`
        } else if (token[0] === ' ' || token[0] === '\t') {
          // Flag (starts with whitespace then -)
          result += `${escapeHtml(token[0])}<span class="text-yellow-400">${escapeHtml(token.slice(1))}</span>`
        } else if (firstWord && commands.has(token)) {
          result += `<span class="text-cyan-400">${escapeHtml(token)}</span>`
          firstWord = false
        } else {
          result += escapeHtml(token)
          firstWord = false
        }

        lastIndex = match.index + match[0].length
      }

      result += escapeHtml(line.slice(lastIndex))
      return result
    })
    .join('\n')
}

function highlightLogs(code) {
  return code
    .split('\n')
    .map(line => {
      let result = escapeHtml(line)

      // Log levels
      result = result.replace(/\bINFO\b/g, '<span class="text-blue-400">INFO</span>')
      result = result.replace(/\bWARN\b/g, '<span class="text-yellow-400">WARN</span>')
      result = result.replace(/\bERROR\b/g, '<span class="text-red-400">ERROR</span>')
      result = result.replace(/\bDEBUG\b/g, '<span class="text-gray-400">DEBUG</span>')

      // Event types
      result = result.replace(/RECONCILIATION_START/g, '<span class="text-cyan-400">RECONCILIATION_START</span>')
      result = result.replace(/RECONCILIATION_END/g, '<span class="text-purple-400">RECONCILIATION_END</span>')
      result = result.replace(/\bSUCCESS\b/g, '<span class="text-green-400">SUCCESS</span>')
      result = result.replace(/\bFAILED\b/g, '<span class="text-red-400">FAILED</span>')

      // Operations
      result = result.replace(/\b(CREATE|UPDATE|DELETE)\b/g, '<span class="text-yellow-400">$1</span>')

      // Resource types
      result = result.replace(/\b(ApplicationService|KafkaCluster|ServiceAccount|Topic|ACL|ConsumerGroup)\b/g,
        '<span class="text-orange-400">$1</span>')

      // Namespace/name
      result = result.replace(/(\s)(default\/[a-zA-Z0-9-]+)/g, '$1<span class="text-green-300">$2</span>')
      result = result.replace(/(\s)(test-namespace\/[a-zA-Z0-9-]+)/g, '$1<span class="text-green-300">$2</span>')

      return result
    })
    .join('\n')
}

function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

function highlight(code, language) {
  switch (language) {
    case 'yaml':
      return highlightYaml(code)
    case 'java':
      return highlightJava(code)
    case 'bash':
    case 'sh':
    case 'shell':
      return highlightBash(code)
    case 'log':
    case 'logs':
      return highlightLogs(code)
    default:
      // Auto-detect based on content
      if (code.includes('apiVersion:') || code.includes('kind:')) {
        return highlightYaml(code)
      }
      if (code.includes('ReconciliationEventPublisher') || code.includes('RECONCILIATION_')) {
        return highlightLogs(code)
      }
      if (code.includes('public ') || code.includes('private ') || code.includes('class ')) {
        return highlightJava(code)
      }
      if (code.includes('kubectl') || code.includes('grep') || code.includes('git') || code.includes('conduktor')) {
        return highlightBash(code)
      }
      return escapeHtml(code)
  }
}

export default function CodeBlock({ code, language = 'auto', title, className = '' }) {
  const [copied, setCopied] = useState(false)

  const handleCopy = () => {
    navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const highlighted = highlight(code, language)

  return (
    <div className={`rounded-2xl overflow-hidden bg-gray-900 dark:bg-gray-950 ${className}`}>
      {title && (
        <div className="flex items-center justify-between px-4 py-3 bg-gray-800 dark:bg-gray-900 border-b border-gray-700">
          <div className="flex items-center gap-2 text-gray-400">
            {language === 'bash' || language === 'sh' ? <Terminal size={16} /> : <FileCode size={16} />}
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
        <code
          className="text-gray-300 font-mono"
          dangerouslySetInnerHTML={{ __html: highlighted }}
        />
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
