import { CheckCircle2, XCircle, Clock, AlertCircle } from 'lucide-react'

export default function TestResult({ name, status, duration, description, steps }) {
  const statusConfig = {
    passed: {
      icon: CheckCircle2,
      color: 'text-green-500',
      bg: 'bg-green-50 dark:bg-green-900/20',
      border: 'border-green-200 dark:border-green-800',
      label: 'Passed'
    },
    failed: {
      icon: XCircle,
      color: 'text-red-500',
      bg: 'bg-red-50 dark:bg-red-900/20',
      border: 'border-red-200 dark:border-red-800',
      label: 'Failed'
    },
    pending: {
      icon: Clock,
      color: 'text-yellow-500',
      bg: 'bg-yellow-50 dark:bg-yellow-900/20',
      border: 'border-yellow-200 dark:border-yellow-800',
      label: 'Pending'
    },
    skipped: {
      icon: AlertCircle,
      color: 'text-gray-400',
      bg: 'bg-gray-50 dark:bg-gray-900/20',
      border: 'border-gray-200 dark:border-gray-800',
      label: 'Skipped'
    }
  }

  const config = statusConfig[status] || statusConfig.pending
  const Icon = config.icon

  return (
    <div className={`rounded-xl border ${config.border} ${config.bg} overflow-hidden`}>
      <div className="flex items-center justify-between p-4">
        <div className="flex items-center gap-3">
          <Icon className={config.color} size={20} />
          <div>
            <div className="font-medium">{name}</div>
            {description && (
              <div className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{description}</div>
            )}
          </div>
        </div>
        <div className="flex items-center gap-4">
          {duration && (
            <span className="text-sm text-gray-500 dark:text-gray-400">{duration}</span>
          )}
          <span className={`text-sm font-medium ${config.color}`}>{config.label}</span>
        </div>
      </div>

      {steps && steps.length > 0 && (
        <div className="border-t border-gray-200 dark:border-gray-700 px-4 py-3 space-y-2">
          {steps.map((step, index) => (
            <div key={index} className="flex items-start gap-2 text-sm">
              <span className="text-gray-400 font-mono">{index + 1}.</span>
              <div>
                <span className="text-blue-600 dark:text-blue-400 font-medium">{step.keyword}</span>
                <span className="text-gray-700 dark:text-gray-300 ml-1">{step.text}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export function TestSuite({ name, tests, stats }) {
  const passedCount = tests.filter(t => t.status === 'passed').length
  const failedCount = tests.filter(t => t.status === 'failed').length

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">{name}</h3>
        <div className="flex items-center gap-4 text-sm">
          <span className="text-green-500">{passedCount} passed</span>
          {failedCount > 0 && <span className="text-red-500">{failedCount} failed</span>}
          {stats?.duration && <span className="text-gray-500">{stats.duration}</span>}
        </div>
      </div>
      <div className="space-y-3">
        {tests.map((test, index) => (
          <TestResult key={index} {...test} />
        ))}
      </div>
    </div>
  )
}

export function TestStats({ total, passed, failed, skipped, duration }) {
  const passRate = total > 0 ? Math.round((passed / total) * 100) : 0

  return (
    <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
      <div className="bg-white dark:bg-gray-900 rounded-xl p-4 border border-gray-200 dark:border-gray-800 text-center">
        <div className="text-3xl font-bold text-gray-900 dark:text-white">{total}</div>
        <div className="text-sm text-gray-500 mt-1">Total Tests</div>
      </div>
      <div className="bg-green-50 dark:bg-green-900/20 rounded-xl p-4 border border-green-200 dark:border-green-800 text-center">
        <div className="text-3xl font-bold text-green-600 dark:text-green-400">{passed}</div>
        <div className="text-sm text-green-600 dark:text-green-400 mt-1">Passed</div>
      </div>
      <div className="bg-red-50 dark:bg-red-900/20 rounded-xl p-4 border border-red-200 dark:border-red-800 text-center">
        <div className="text-3xl font-bold text-red-600 dark:text-red-400">{failed}</div>
        <div className="text-sm text-red-600 dark:text-red-400 mt-1">Failed</div>
      </div>
      <div className="bg-gray-50 dark:bg-gray-900/20 rounded-xl p-4 border border-gray-200 dark:border-gray-800 text-center">
        <div className="text-3xl font-bold text-gray-600 dark:text-gray-400">{skipped}</div>
        <div className="text-sm text-gray-500 mt-1">Skipped</div>
      </div>
      <div className="bg-blue-50 dark:bg-blue-900/20 rounded-xl p-4 border border-blue-200 dark:border-blue-800 text-center">
        <div className="text-3xl font-bold text-blue-600 dark:text-blue-400">{passRate}%</div>
        <div className="text-sm text-blue-600 dark:text-blue-400 mt-1">Pass Rate</div>
      </div>
    </div>
  )
}
