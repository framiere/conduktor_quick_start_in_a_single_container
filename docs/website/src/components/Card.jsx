export default function Card({ title, children, icon: Icon, className = '', variant = 'default' }) {
  const variants = {
    default: 'bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800',
    gradient: 'bg-gradient-to-br from-blue-50 to-purple-50 dark:from-blue-900/20 dark:to-purple-900/20 border border-blue-200 dark:border-blue-800',
    success: 'bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800',
    warning: 'bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800',
    error: 'bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800',
  }

  return (
    <div className={`rounded-2xl p-6 card-hover ${variants[variant]} ${className}`}>
      {(title || Icon) && (
        <div className="flex items-center gap-3 mb-4">
          {Icon && (
            <div className="p-2 bg-blue-100 dark:bg-blue-900/50 rounded-xl text-blue-600 dark:text-blue-400">
              <Icon size={20} />
            </div>
          )}
          {title && <h3 className="text-lg font-semibold">{title}</h3>}
        </div>
      )}
      {children}
    </div>
  )
}

export function CardGrid({ children, cols = 2 }) {
  const colsClass = {
    1: 'grid-cols-1',
    2: 'grid-cols-1 md:grid-cols-2',
    3: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3',
    4: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-4',
  }

  return (
    <div className={`grid ${colsClass[cols]} gap-6`}>
      {children}
    </div>
  )
}
