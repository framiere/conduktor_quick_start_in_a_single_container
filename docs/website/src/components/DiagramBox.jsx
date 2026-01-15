import { useState } from 'react'
import { ZoomIn, ZoomOut, Maximize2 } from 'lucide-react'

export default function DiagramBox({ title, children, className = '' }) {
  const [isExpanded, setIsExpanded] = useState(false)

  return (
    <>
      <div className={`bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 overflow-hidden ${className}`}>
        {title && (
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-800">
            <h3 className="font-semibold">{title}</h3>
            <button
              onClick={() => setIsExpanded(true)}
              className="p-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg text-gray-500"
              title="Expand"
            >
              <Maximize2 size={18} />
            </button>
          </div>
        )}
        <div className="p-6 overflow-x-auto">
          {children}
        </div>
      </div>

      {/* Fullscreen modal */}
      {isExpanded && (
        <div
          className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-8"
          onClick={() => setIsExpanded(false)}
        >
          <div
            className="bg-white dark:bg-gray-900 rounded-2xl max-w-[95vw] max-h-[95vh] overflow-auto"
            onClick={e => e.stopPropagation()}
          >
            {title && (
              <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-800 sticky top-0 bg-white dark:bg-gray-900">
                <h3 className="font-semibold">{title}</h3>
                <button
                  onClick={() => setIsExpanded(false)}
                  className="p-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg text-gray-500"
                >
                  Close
                </button>
              </div>
            )}
            <div className="p-8">
              {children}
            </div>
          </div>
        </div>
      )}
    </>
  )
}

// Simple box component for diagram nodes
export function DiagramNode({ children, color = 'blue', className = '' }) {
  const colors = {
    blue: 'bg-blue-100 dark:bg-blue-900/50 border-blue-300 dark:border-blue-700 text-blue-800 dark:text-blue-200',
    green: 'bg-green-100 dark:bg-green-900/50 border-green-300 dark:border-green-700 text-green-800 dark:text-green-200',
    purple: 'bg-purple-100 dark:bg-purple-900/50 border-purple-300 dark:border-purple-700 text-purple-800 dark:text-purple-200',
    orange: 'bg-orange-100 dark:bg-orange-900/50 border-orange-300 dark:border-orange-700 text-orange-800 dark:text-orange-200',
    red: 'bg-red-100 dark:bg-red-900/50 border-red-300 dark:border-red-700 text-red-800 dark:text-red-200',
    gray: 'bg-gray-100 dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-gray-800 dark:text-gray-200',
    cyan: 'bg-cyan-100 dark:bg-cyan-900/50 border-cyan-300 dark:border-cyan-700 text-cyan-800 dark:text-cyan-200',
  }

  return (
    <div className={`px-4 py-3 rounded-xl border-2 font-medium text-center ${colors[color]} ${className}`}>
      {children}
    </div>
  )
}

// Arrow component for connections
export function DiagramArrow({ direction = 'down', label, className = '' }) {
  const arrows = {
    down: '↓',
    up: '↑',
    right: '→',
    left: '←',
    'down-right': '↘',
    'down-left': '↙',
  }

  return (
    <div className={`flex flex-col items-center text-gray-400 dark:text-gray-500 ${className}`}>
      <span className="text-2xl">{arrows[direction]}</span>
      {label && <span className="text-xs mt-1">{label}</span>}
    </div>
  )
}
