import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeHighlight from 'rehype-highlight'
import 'highlight.js/styles/github-dark.css'

export default function MarkdownRenderer({ content, className = '' }) {
  return (
    <div className={`markdown-content ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeHighlight]}
        components={{
          // Headings
          h1: ({ children }) => (
            <h1 className="text-4xl lg:text-5xl font-mono font-bold mb-8 mt-12 text-gray-900 dark:text-gray-100 tracking-tight border-b-2 border-amber-500 pb-4">
              {children}
            </h1>
          ),
          h2: ({ children }) => (
            <h2 className="text-3xl lg:text-4xl font-mono font-bold mb-6 mt-10 text-gray-800 dark:text-gray-200 tracking-tight">
              {children}
            </h2>
          ),
          h3: ({ children }) => (
            <h3 className="text-2xl font-mono font-semibold mb-4 mt-8 text-gray-800 dark:text-gray-200">
              {children}
            </h3>
          ),
          h4: ({ children }) => (
            <h4 className="text-xl font-mono font-semibold mb-3 mt-6 text-gray-700 dark:text-gray-300">
              {children}
            </h4>
          ),

          // Paragraphs & text
          p: ({ children }) => (
            <p className="text-lg leading-relaxed mb-6 text-gray-700 dark:text-gray-300 font-serif">
              {children}
            </p>
          ),

          // Lists
          ul: ({ children }) => (
            <ul className="list-none space-y-3 mb-6 ml-0 text-gray-700 dark:text-gray-300">
              {children}
            </ul>
          ),
          ol: ({ children }) => (
            <ol className="list-decimal list-inside space-y-3 mb-6 ml-4 text-gray-700 dark:text-gray-300">
              {children}
            </ol>
          ),
          li: ({ children, ordered }) => (
            <li className="flex items-start gap-3 text-lg leading-relaxed font-serif">
              {!ordered && (
                <span className="text-amber-500 text-2xl leading-none mt-1">•</span>
              )}
              <span className="flex-1">{children}</span>
            </li>
          ),

          // Code
          code: ({ inline, children, className }) => {
            if (inline) {
              return (
                <code className="px-2 py-1 bg-gray-100 dark:bg-gray-800 text-amber-600 dark:text-amber-400 rounded font-mono text-base border border-gray-200 dark:border-gray-700">
                  {children}
                </code>
              )
            }
            return (
              <code className={`${className} font-mono text-sm`}>
                {children}
              </code>
            )
          },
          pre: ({ children }) => (
            <pre className="bg-gray-900 dark:bg-black rounded-xl p-6 mb-6 overflow-x-auto border border-gray-700 shadow-lg">
              {children}
            </pre>
          ),

          // Links
          a: ({ href, children }) => (
            <a
              href={href}
              className="text-amber-600 dark:text-amber-400 underline decoration-2 decoration-amber-500/30 hover:decoration-amber-500 underline-offset-4 transition-all font-medium"
              target={href?.startsWith('http') ? '_blank' : undefined}
              rel={href?.startsWith('http') ? 'noopener noreferrer' : undefined}
            >
              {children}
            </a>
          ),

          // Blockquotes
          blockquote: ({ children }) => (
            <blockquote className="border-l-4 border-amber-500 pl-6 py-2 mb-6 italic text-gray-600 dark:text-gray-400 bg-gray-50 dark:bg-gray-900/50 rounded-r-lg">
              {children}
            </blockquote>
          ),

          // Tables
          table: ({ children }) => (
            <div className="overflow-x-auto mb-6">
              <table className="w-full border-collapse">
                {children}
              </table>
            </div>
          ),
          thead: ({ children }) => (
            <thead className="bg-gray-100 dark:bg-gray-800">
              {children}
            </thead>
          ),
          tbody: ({ children }) => (
            <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
              {children}
            </tbody>
          ),
          tr: ({ children }) => (
            <tr className="hover:bg-gray-50 dark:hover:bg-gray-900/50 transition-colors">
              {children}
            </tr>
          ),
          th: ({ children }) => (
            <th className="px-4 py-3 text-left font-mono font-semibold text-sm text-gray-900 dark:text-gray-100 border-b-2 border-amber-500">
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className="px-4 py-3 text-gray-700 dark:text-gray-300">
              {children}
            </td>
          ),

          // Horizontal rule
          hr: () => (
            <hr className="my-12 border-0 h-px bg-gradient-to-r from-transparent via-amber-500 to-transparent" />
          ),

          // Images
          img: ({ src, alt }) => (
            <img
              src={src}
              alt={alt}
              className="rounded-xl shadow-lg mb-6 w-full border border-gray-200 dark:border-gray-800"
              loading="lazy"
            />
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}
