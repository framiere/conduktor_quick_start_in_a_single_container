import { useState } from 'react'
import { FlaskConical, CheckCircle2, XCircle, Clock, ChevronDown, ChevronRight, Layers, Zap, Box, Activity } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import Section from '../components/Section'
import testResults from '../data/testResults.json'

const categoryIcons = {
  CRD: Box,
  Store: Layers,
  Validation: CheckCircle2,
  Webhook: Zap,
  Events: Activity,
  E2E: FlaskConical,
  Scenario: FlaskConical,
  Component: Layers,
  Other: Box
}

const categoryColors = {
  CRD: 'blue',
  Store: 'purple',
  Validation: 'green',
  Webhook: 'orange',
  Events: 'pink',
  E2E: 'red',
  Scenario: 'cyan',
  Component: 'indigo',
  Other: 'gray'
}

function StatCard({ label, value, subValue, icon: Icon, color }) {
  const colorClasses = {
    blue: 'from-blue-500 to-blue-600',
    green: 'from-green-500 to-green-600',
    red: 'from-red-500 to-red-600',
    orange: 'from-orange-500 to-orange-600',
    purple: 'from-purple-500 to-purple-600'
  }

  return (
    <div className="relative overflow-hidden rounded-2xl bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 p-6">
      <div className={`absolute top-0 right-0 w-32 h-32 bg-gradient-to-br ${colorClasses[color] || colorClasses.blue} opacity-10 rounded-full transform translate-x-8 -translate-y-8`} />
      <div className="relative">
        <div className={`inline-flex p-3 rounded-xl bg-gradient-to-br ${colorClasses[color] || colorClasses.blue}`}>
          <Icon className="text-white" size={24} />
        </div>
        <div className="mt-4">
          <div className="text-3xl font-bold">{value}</div>
          {subValue && <div className="text-sm text-gray-500 dark:text-gray-400">{subValue}</div>}
          <div className="text-sm text-gray-600 dark:text-gray-400 mt-1">{label}</div>
        </div>
      </div>
    </div>
  )
}

function CategoryBar({ category, data }) {
  const Icon = categoryIcons[category] || Box
  const color = categoryColors[category] || 'gray'
  const passRate = data.tests > 0 ? Math.round((data.passed / data.tests) * 100) : 0

  const colorClasses = {
    blue: 'bg-blue-500',
    green: 'bg-green-500',
    purple: 'bg-purple-500',
    orange: 'bg-orange-500',
    pink: 'bg-pink-500',
    red: 'bg-red-500',
    cyan: 'bg-cyan-500',
    indigo: 'bg-indigo-500',
    gray: 'bg-gray-500'
  }

  return (
    <div className="p-4 bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-3">
          <div className={`p-2 rounded-lg ${colorClasses[color]} bg-opacity-20 dark:bg-opacity-30`}>
            <Icon size={18} className={colorClasses[color].replace('bg-', 'text-')} />
          </div>
          <span className="font-medium">{category}</span>
        </div>
        <div className="text-sm text-gray-500">
          {data.passed}/{data.tests} passed
        </div>
      </div>
      <div className="h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          className={`h-full ${passRate === 100 ? 'bg-green-500' : 'bg-orange-500'} transition-all duration-500`}
          style={{ width: `${passRate}%` }}
        />
      </div>
      <div className="mt-2 flex justify-between text-xs text-gray-500">
        <span>{passRate}% pass rate</span>
        <span>{data.time.toFixed(2)}s</span>
      </div>
    </div>
  )
}

function TestSuiteRow({ suite, isExpanded, onToggle }) {
  const shortName = suite.name.split('.').pop().replace('$', ' › ')
  const passRate = suite.tests > 0 ? Math.round((suite.passed / suite.tests) * 100) : 0
  const isAllPassed = suite.failures === 0 && suite.errors === 0

  return (
    <div className="border-b border-gray-200 dark:border-gray-800 last:border-b-0">
      <button
        onClick={onToggle}
        className="w-full px-4 py-3 flex items-center gap-3 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors text-left"
      >
        {suite.testCases.length > 0 ? (
          isExpanded ? <ChevronDown size={16} className="text-gray-400" /> : <ChevronRight size={16} className="text-gray-400" />
        ) : (
          <div className="w-4" />
        )}

        <div className={`w-2 h-2 rounded-full ${isAllPassed ? 'bg-green-500' : 'bg-red-500'}`} />

        <div className="flex-1 min-w-0">
          <div className="font-medium truncate">{shortName}</div>
          <div className="text-xs text-gray-500 truncate">{suite.name}</div>
        </div>

        <div className="flex items-center gap-4 text-sm">
          <span className={`px-2 py-0.5 rounded text-xs ${suite.type === 'unit' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' : 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400'}`}>
            {suite.type}
          </span>
          <span className="text-gray-500 w-16 text-right">{suite.tests} tests</span>
          <span className="text-gray-500 w-16 text-right">{suite.time.toFixed(3)}s</span>
          <span className={`w-12 text-right font-medium ${isAllPassed ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
            {passRate}%
          </span>
        </div>
      </button>

      {isExpanded && suite.testCases.length > 0 && (
        <div className="bg-gray-50 dark:bg-gray-800/30 px-4 py-2">
          {suite.testCases.map((tc, idx) => (
            <div key={idx} className="flex items-center gap-3 py-2 text-sm border-b border-gray-200 dark:border-gray-700 last:border-b-0">
              {tc.status === 'passed' ? (
                <CheckCircle2 size={16} className="text-green-500 shrink-0" />
              ) : tc.status === 'failed' ? (
                <XCircle size={16} className="text-red-500 shrink-0" />
              ) : (
                <div className="w-4 h-4 rounded-full border-2 border-gray-300 shrink-0" />
              )}
              <span className="flex-1 truncate">{tc.name}</span>
              <span className="text-gray-500 text-xs">{tc.time.toFixed(3)}s</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function TestResultsPage() {
  const [expandedSuites, setExpandedSuites] = useState(new Set())
  const [filter, setFilter] = useState('all')
  const [typeFilter, setTypeFilter] = useState('all')

  const { summary, byCategory, suites } = testResults

  const totalTests = summary.unit.tests + summary.integration.tests
  const totalPassed = summary.unit.passed + summary.integration.passed
  const totalFailed = summary.unit.failed + summary.integration.failed + summary.unit.errors + summary.integration.errors
  const totalTime = summary.unit.time + summary.integration.time

  const toggleSuite = (name) => {
    const newExpanded = new Set(expandedSuites)
    if (newExpanded.has(name)) {
      newExpanded.delete(name)
    } else {
      newExpanded.add(name)
    }
    setExpandedSuites(newExpanded)
  }

  const filteredSuites = suites.filter(suite => {
    if (filter !== 'all' && suite.category !== filter) return false
    if (typeFilter !== 'all' && suite.type !== typeFilter) return false
    return true
  })

  const categories = Object.keys(byCategory).sort()

  return (
    <PageLayout
      title="Test Results"
      subtitle={`Last run: ${new Date(summary.timestamp).toLocaleString()}`}
      icon={FlaskConical}
      breadcrumb="Test Results"
    >
      {/* Summary Stats */}
      <Section title="Summary" subtitle="Overall test execution statistics">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard
            label="Total Tests"
            value={totalTests}
            subValue={`${suites.length} test suites`}
            icon={FlaskConical}
            color="blue"
          />
          <StatCard
            label="Passed"
            value={totalPassed}
            subValue={`${Math.round((totalPassed / totalTests) * 100)}% pass rate`}
            icon={CheckCircle2}
            color="green"
          />
          <StatCard
            label="Failed"
            value={totalFailed}
            subValue={totalFailed === 0 ? 'All tests passing' : 'Needs attention'}
            icon={XCircle}
            color={totalFailed === 0 ? 'green' : 'red'}
          />
          <StatCard
            label="Total Time"
            value={`${totalTime.toFixed(1)}s`}
            subValue={`${(totalTests / totalTime).toFixed(1)} tests/sec`}
            icon={Clock}
            color="purple"
          />
        </div>
      </Section>

      {/* Unit vs Integration */}
      <Section title="Test Types" subtitle="Distribution between unit and integration tests">
        <CardGrid cols={2}>
          <Card>
            <div className="flex items-center gap-4">
              <div className="p-3 rounded-xl bg-blue-100 dark:bg-blue-900/30">
                <Zap className="text-blue-600 dark:text-blue-400" size={24} />
              </div>
              <div className="flex-1">
                <div className="text-2xl font-bold">{summary.unit.tests}</div>
                <div className="text-sm text-gray-500">Unit Tests</div>
              </div>
              <div className="text-right">
                <div className="text-lg font-semibold text-green-600 dark:text-green-400">
                  {summary.unit.passed} passed
                </div>
                <div className="text-sm text-gray-500">{summary.unit.time.toFixed(2)}s</div>
              </div>
            </div>
            <div className="mt-4 h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
              <div
                className="h-full bg-green-500"
                style={{ width: `${(summary.unit.passed / summary.unit.tests) * 100}%` }}
              />
            </div>
          </Card>
          <Card>
            <div className="flex items-center gap-4">
              <div className="p-3 rounded-xl bg-purple-100 dark:bg-purple-900/30">
                <Layers className="text-purple-600 dark:text-purple-400" size={24} />
              </div>
              <div className="flex-1">
                <div className="text-2xl font-bold">{summary.integration.tests}</div>
                <div className="text-sm text-gray-500">Integration Tests</div>
              </div>
              <div className="text-right">
                <div className="text-lg font-semibold text-green-600 dark:text-green-400">
                  {summary.integration.passed} passed
                </div>
                <div className="text-sm text-gray-500">{summary.integration.time.toFixed(2)}s</div>
              </div>
            </div>
            <div className="mt-4 h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
              <div
                className="h-full bg-green-500"
                style={{ width: `${(summary.integration.passed / summary.integration.tests) * 100}%` }}
              />
            </div>
          </Card>
        </CardGrid>
      </Section>

      {/* Category Breakdown */}
      <Section title="By Category" subtitle="Test coverage across different modules">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {categories.map(cat => (
            <CategoryBar key={cat} category={cat} data={byCategory[cat]} />
          ))}
        </div>
      </Section>

      {/* Detailed Results */}
      <Section title="Test Suites" subtitle="Detailed breakdown of all test suites">
        <div className="mb-4 flex flex-wrap gap-3">
          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm"
          >
            <option value="all">All Categories</option>
            {categories.map(cat => (
              <option key={cat} value={cat}>{cat}</option>
            ))}
          </select>
          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm"
          >
            <option value="all">All Types</option>
            <option value="unit">Unit Tests</option>
            <option value="integration">Integration Tests</option>
          </select>
          <div className="text-sm text-gray-500 flex items-center ml-auto">
            Showing {filteredSuites.length} of {suites.length} suites
          </div>
        </div>

        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 overflow-hidden">
          <div className="px-4 py-3 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 flex items-center gap-3 text-sm font-medium text-gray-500">
            <div className="w-4" />
            <div className="w-2" />
            <div className="flex-1">Test Suite</div>
            <div className="w-16 text-right">Type</div>
            <div className="w-16 text-right">Tests</div>
            <div className="w-16 text-right">Time</div>
            <div className="w-12 text-right">Pass</div>
          </div>
          <div className="max-h-[600px] overflow-y-auto">
            {filteredSuites.map(suite => (
              <TestSuiteRow
                key={suite.name}
                suite={suite}
                isExpanded={expandedSuites.has(suite.name)}
                onToggle={() => toggleSuite(suite.name)}
              />
            ))}
          </div>
        </div>
      </Section>

      {/* Pass Rate Visual */}
      <Section title="Overall Health">
        <Card>
          <div className="flex items-center justify-center py-8">
            <div className="relative">
              <svg className="w-48 h-48 transform -rotate-90">
                <circle
                  cx="96"
                  cy="96"
                  r="88"
                  stroke="currentColor"
                  strokeWidth="12"
                  fill="none"
                  className="text-gray-200 dark:text-gray-700"
                />
                <circle
                  cx="96"
                  cy="96"
                  r="88"
                  stroke="currentColor"
                  strokeWidth="12"
                  fill="none"
                  strokeDasharray={`${(totalPassed / totalTests) * 553} 553`}
                  className="text-green-500"
                  strokeLinecap="round"
                />
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center">
                <div className="text-4xl font-bold">
                  {Math.round((totalPassed / totalTests) * 100)}%
                </div>
                <div className="text-sm text-gray-500">Pass Rate</div>
              </div>
            </div>
          </div>
          <div className="text-center text-gray-600 dark:text-gray-400">
            {totalFailed === 0 ? (
              <div className="flex items-center justify-center gap-2 text-green-600 dark:text-green-400">
                <CheckCircle2 size={20} />
                <span>All {totalTests} tests passing</span>
              </div>
            ) : (
              <div className="flex items-center justify-center gap-2 text-red-600 dark:text-red-400">
                <XCircle size={20} />
                <span>{totalFailed} tests failing</span>
              </div>
            )}
          </div>
        </Card>
      </Section>
    </PageLayout>
  )
}
