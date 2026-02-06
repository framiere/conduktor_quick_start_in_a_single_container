import { ChevronRight, Quote } from 'lucide-react'
import useScrollToHash from '../hooks/useScrollToHash'
import TableOfContents from './TableOfContents'

export default function PageLayout({ title, subtitle, icon: Icon, children, breadcrumb, aphorism }) {
  useScrollToHash()

  return (
    <div className="animate-fade-in">
      {/* Hero section */}
      <div className="bg-gradient-to-br from-blue-500 via-purple-500 to-pink-500 text-white">
        <div className="max-w-6xl mx-auto px-6 py-16 lg:py-24">
          {breadcrumb && (
            <div className="flex items-center gap-2 text-blue-100 text-sm mb-4">
              <span>Documentation</span>
              <ChevronRight size={14} />
              <span>{breadcrumb}</span>
            </div>
          )}
          <div className="flex items-center gap-4 mb-4">
            {Icon && (
              <div className="p-3 bg-white/20 rounded-2xl backdrop-blur-sm">
                <Icon size={32} />
              </div>
            )}
            <h1 className="text-4xl lg:text-5xl font-bold">{title}</h1>
          </div>
          <p className="text-xl text-blue-100 max-w-2xl">{subtitle}</p>
          {aphorism && (
            <div className="mt-8 flex items-start gap-3 max-w-2xl">
              <Quote size={20} className="text-white/40 shrink-0 mt-1" />
              <blockquote className="text-white/80 italic text-lg leading-relaxed">
                {aphorism.text}
                {aphorism.author && (
                  <footer className="mt-2 text-white/60 text-sm not-italic">
                    — {aphorism.author}
                  </footer>
                )}
              </blockquote>
            </div>
          )}
        </div>
      </div>

      {/* Content */}
      <div className="max-w-6xl mx-auto px-6 py-12">
        {children}
      </div>

      <TableOfContents />
    </div>
  )
}
