import { ChevronRight } from 'lucide-react'

export default function PageLayout({ title, subtitle, icon: Icon, children, breadcrumb }) {
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
        </div>
      </div>

      {/* Content */}
      <div className="max-w-6xl mx-auto px-6 py-12">
        {children}
      </div>
    </div>
  )
}
