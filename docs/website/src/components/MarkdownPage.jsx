import { useState, useEffect } from 'react'
import PageLayout from './PageLayout'
import MarkdownRenderer from './MarkdownRenderer'
import { FileText, Clock } from 'lucide-react'

export default function MarkdownPage({ title, description, markdownFile }) {
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!markdownFile) {
      setContent('')
      setLoading(false)
      return
    }

    fetch(markdownFile)
      .then(response => {
        if (!response.ok) {
          throw new Error('Failed to load markdown file')
        }
        return response.text()
      })
      .then(text => {
        setContent(text)
        setLoading(false)
      })
      .catch(err => {
        setError(err.message)
        setLoading(false)
      })
  }, [markdownFile])

  if (loading) {
    return (
      <PageLayout title={title} description={description}>
        <div className="flex items-center justify-center min-h-[400px]">
          <div className="flex flex-col items-center gap-4">
            <div className="w-12 h-12 border-4 border-amber-500 border-t-transparent rounded-full animate-spin"></div>
            <p className="text-gray-500 dark:text-gray-400 font-mono">Loading documentation...</p>
          </div>
        </div>
      </PageLayout>
    )
  }

  if (error) {
    return (
      <PageLayout title={title} description={description}>
        <div className="flex items-center justify-center min-h-[400px]">
          <div className="text-center">
            <FileText className="w-16 h-16 text-gray-300 dark:text-gray-700 mx-auto mb-4" />
            <p className="text-gray-500 dark:text-gray-400 font-mono">Failed to load content</p>
            <p className="text-sm text-gray-400 dark:text-gray-600 mt-2">{error}</p>
          </div>
        </div>
      </PageLayout>
    )
  }

  return (
    <PageLayout title={title} description={description}>
      <div className="max-w-4xl mx-auto">
        <MarkdownRenderer content={content} />

        {/* Reading time estimate */}
        {content && (
          <div className="mt-12 pt-8 border-t border-gray-200 dark:border-gray-800">
            <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400 font-mono">
              <Clock size={16} />
              <span>
                Estimated reading time: {Math.max(1, Math.ceil(content.split(/\s+/).length / 200))} min
              </span>
            </div>
          </div>
        )}
      </div>
    </PageLayout>
  )
}
