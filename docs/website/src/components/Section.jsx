import { Link } from 'lucide-react'
import { useLocation } from 'react-router-dom'
import slugify from '../utils/slugify'

export default function Section({ title, subtitle, children, className = '', id: explicitId }) {
  const { pathname } = useLocation()
  const slug = explicitId || (title ? slugify(title) : undefined)
  const href = slug ? `#${pathname}#${slug}` : undefined

  return (
    <section id={slug} className={`mb-12 scroll-mt-24 ${className}`}>
      {(title || subtitle) && (
        <div className="mb-6">
          {title && (
            <h2 className="text-2xl font-bold group flex items-center gap-2">
              {title}
              {href && (
                <a href={href} className="opacity-0 group-hover:opacity-60 transition-opacity" aria-label={`Link to ${title}`}>
                  <Link size={18} />
                </a>
              )}
            </h2>
          )}
          {subtitle && <p className="text-gray-600 dark:text-gray-400 mt-2">{subtitle}</p>}
        </div>
      )}
      {children}
    </section>
  )
}

export function SectionDivider() {
  return <hr className="my-12 border-gray-200 dark:border-gray-800" />
}
