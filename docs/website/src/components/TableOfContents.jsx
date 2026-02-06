import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useLocation } from 'react-router-dom'
import { List } from 'lucide-react'

export default function TableOfContents() {
  const { pathname } = useLocation()
  const [entries, setEntries] = useState([])
  const [activeId, setActiveId] = useState(null)
  const observerRef = useRef(null)

  // Scan DOM for h2 and h3 headings with an id (on the heading itself or parent section)
  useEffect(() => {
    const timeout = setTimeout(() => {
      const found = []
      const content = document.querySelector('.animate-fade-in')
      if (!content) return

      content.querySelectorAll('h2, h3').forEach(heading => {
        const text = heading.textContent?.trim() || ''
        if (!text) return

        // Find an anchor id: the heading itself, or the closest section with an id
        const id = heading.id || heading.closest('section[id]')?.id
        if (!id) return

        found.push({ id, title: text, level: heading.tagName === 'H2' ? 2 : 3 })
      })

      // Deduplicate by id (keep first occurrence)
      const seen = new Set()
      setEntries(found.filter(e => {
        if (seen.has(e.id)) return false
        seen.add(e.id)
        return true
      }))
    }, 150)

    return () => clearTimeout(timeout)
  }, [pathname])

  // Track active heading via IntersectionObserver
  useEffect(() => {
    if (entries.length <= 3) return

    observerRef.current?.disconnect()

    const observer = new IntersectionObserver(
      (intersections) => {
        const visible = intersections
          .filter(e => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
        if (visible.length > 0) {
          setActiveId(visible[0].target.id)
        }
      },
      { rootMargin: '-80px 0px -60% 0px', threshold: 0 }
    )

    observerRef.current = observer
    entries.forEach(({ id }) => {
      const el = document.getElementById(id)
      if (el) observer.observe(el)
    })

    return () => observer.disconnect()
  }, [entries])

  if (entries.length <= 3) return null

  // Portal to document.body to escape animate-fade-in's transform context
  return createPortal(
    <nav className="fixed right-6 top-24 z-30 max-h-[70vh] overflow-y-auto">
      <div className="bg-white/80 dark:bg-gray-900/80 backdrop-blur-xl rounded-xl border border-gray-200 dark:border-gray-800 p-3 w-52 shadow-lg">
        <div className="flex items-center gap-2 text-xs font-semibold text-gray-400 mb-2 px-2">
          <List size={12} />
          <span>On this page</span>
        </div>
        <ul className="space-y-0.5">
          {entries.map(({ id, title, level }) => (
            <li key={id}>
              <a
                href={`#${pathname}#${id}`}
                className={`block py-1 text-xs rounded-lg transition-colors truncate ${
                  level === 3 ? 'pl-5 pr-2' : 'px-2'
                } ${
                  activeId === id
                    ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400 font-medium'
                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800'
                }`}
                title={title}
              >
                {title}
              </a>
            </li>
          ))}
        </ul>
      </div>
    </nav>,
    document.body
  )
}
