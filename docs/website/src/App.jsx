import { useState } from 'react'
import { HashRouter as Router, Routes, Route, NavLink, useLocation } from 'react-router-dom'
import {
  Home, Building2, Shield, TestTube2, Code2, Server, Boxes, Network,
  Moon, Sun, Menu, X, ChevronRight, Lock, Users, FlaskConical, Activity, Wrench, RefreshCw
} from 'lucide-react'

import './index.css'

// Import pages
import HomePage from './pages/HomePage'
import ArchitecturePage from './pages/ArchitecturePage'
import BusinessPage from './pages/BusinessPage'
import SecurityPage from './pages/SecurityPage'
import TestingPage from './pages/TestingPage'
import DeveloperPage from './pages/DeveloperPage'
import OperationsPage from './pages/OperationsPage'
import CRDPage from './pages/CRDPage'
import WebhookPage from './pages/WebhookPage'
import DataFlowPage from './pages/DataFlowPage'
import TestResultsPage from './pages/TestResultsPage'
import OperabilityPage from './pages/OperabilityPage'
import TestSetupPage from './pages/TestSetupPage'
import TransformationPage from './pages/TransformationPage'
import GatewayPolicyPage from './pages/GatewayPolicyPage'

const perspectives = [
  { path: '/', name: 'Overview', icon: Home, description: 'Project introduction and quick start' },
  { path: '/business', name: 'Business', icon: Users, description: 'Value proposition and use cases' },
  { path: '/architecture', name: 'Architecture', icon: Building2, description: 'System design and components' },
  { path: '/security', name: 'Security', icon: Shield, description: 'mTLS, ownership, and access control' },
  { path: '/testing', name: 'Testing', icon: TestTube2, description: 'Unit, integration, and E2E tests' },
  { path: '/test-setup', name: 'Test Setup', icon: Wrench, description: 'Install Minikube, Helm, Bats' },
  { path: '/test-results', name: 'Test Results', icon: FlaskConical, description: 'Live test execution results' },
  { path: '/developer', name: 'Developer', icon: Code2, description: 'Build, compile, and contribute' },
  { path: '/operations', name: 'Operations', icon: Server, description: 'Deploy and run in Kubernetes' },
  { path: '/crds', name: 'CRDs', icon: Boxes, description: 'Custom Resource Definitions' },
  { path: '/transformation', name: 'Transformation', icon: RefreshCw, description: 'CRD to Conduktor Gateway format' },
  { path: '/gateway-policies', name: 'Gateway Policies', icon: Shield, description: 'Governance and security interceptors' },
  { path: '/webhook', name: 'Webhook', icon: Lock, description: 'Admission controller deep-dive' },
  { path: '/data-flow', name: 'Data Flow', icon: Network, description: 'Request lifecycle visualization' },
  { path: '/operability', name: 'Operability', icon: Activity, description: 'Logs, events, and audit trail' },
]

function Navigation({ isOpen, setIsOpen, darkMode, setDarkMode }) {
  const location = useLocation()

  return (
    <>
      {/* Mobile menu button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="lg:hidden fixed top-4 left-4 z-50 p-2 rounded-xl bg-white dark:bg-gray-800 shadow-lg"
      >
        {isOpen ? <X size={24} /> : <Menu size={24} />}
      </button>

      {/* Sidebar */}
      <aside className={`
        fixed inset-y-0 left-0 z-40 w-72 transform transition-transform duration-300 ease-in-out
        ${isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
        bg-white/80 dark:bg-gray-900/80 backdrop-blur-xl border-r border-gray-200 dark:border-gray-800
      `}>
        <div className="flex flex-col h-full">
          {/* Logo */}
          <div className="p-6 border-b border-gray-200 dark:border-gray-800">
            <h1 className="text-xl font-semibold gradient-text">
              Messaging Operator
            </h1>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
              Kubernetes Multi-Tenant Platform
            </p>
          </div>

          {/* Navigation */}
          <nav className="flex-1 overflow-y-auto p-4 space-y-1">
            {perspectives.map((item) => {
              const Icon = item.icon
              const isActive = location.pathname === item.path
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  onClick={() => setIsOpen(false)}
                  className={`
                    flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-200
                    ${isActive
                      ? 'bg-blue-500 text-white shadow-lg shadow-blue-500/30'
                      : 'hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-700 dark:text-gray-300'
                    }
                  `}
                >
                  <Icon size={20} />
                  <div className="flex-1">
                    <div className="font-medium">{item.name}</div>
                    {isActive && (
                      <div className="text-xs text-blue-100 mt-0.5">{item.description}</div>
                    )}
                  </div>
                  {isActive && <ChevronRight size={16} />}
                </NavLink>
              )
            })}
          </nav>

          {/* Theme toggle */}
          <div className="p-4 border-t border-gray-200 dark:border-gray-800">
            <button
              onClick={() => setDarkMode(!darkMode)}
              className="flex items-center gap-3 w-full px-4 py-3 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-700 dark:text-gray-300"
            >
              {darkMode ? <Sun size={20} /> : <Moon size={20} />}
              <span>{darkMode ? 'Light Mode' : 'Dark Mode'}</span>
            </button>
          </div>
        </div>
      </aside>

      {/* Overlay for mobile */}
      {isOpen && (
        <div
          className="lg:hidden fixed inset-0 z-30 bg-black/50 backdrop-blur-sm"
          onClick={() => setIsOpen(false)}
        />
      )}
    </>
  )
}

function App() {
  const [isOpen, setIsOpen] = useState(false)
  const [darkMode, setDarkMode] = useState(() => {
    if (typeof window !== 'undefined') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    return false
  })

  return (
    <Router>
      <div className={darkMode ? 'dark' : ''}>
        <div className="min-h-screen bg-gray-50 dark:bg-gray-950 text-gray-900 dark:text-gray-100">
          <Navigation
            isOpen={isOpen}
            setIsOpen={setIsOpen}
            darkMode={darkMode}
            setDarkMode={setDarkMode}
          />

          {/* Main content */}
          <main className="lg:pl-72">
            <div className="min-h-screen">
              <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/architecture" element={<ArchitecturePage />} />
                <Route path="/business" element={<BusinessPage />} />
                <Route path="/security" element={<SecurityPage />} />
                <Route path="/testing" element={<TestingPage />} />
                <Route path="/test-setup" element={<TestSetupPage />} />
                <Route path="/test-results" element={<TestResultsPage />} />
                <Route path="/developer" element={<DeveloperPage />} />
                <Route path="/operations" element={<OperationsPage />} />
                <Route path="/crds" element={<CRDPage />} />
                <Route path="/transformation" element={<TransformationPage />} />
                <Route path="/gateway-policies" element={<GatewayPolicyPage />} />
                <Route path="/webhook" element={<WebhookPage />} />
                <Route path="/data-flow" element={<DataFlowPage />} />
                <Route path="/operability" element={<OperabilityPage />} />
              </Routes>
            </div>
          </main>
        </div>
      </div>
    </Router>
  )
}

export default App
