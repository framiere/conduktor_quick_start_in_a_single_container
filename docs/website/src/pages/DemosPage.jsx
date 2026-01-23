import { Play, Terminal, GitBranch, RefreshCw } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Section from '../components/Section'
import AsciinemaPlayer from '../components/AsciinemaPlayer'
import Card from '../components/Card'

const demos = [
  {
    id: 'cli-integration',
    title: 'CLI Integration Demo',
    description: 'Demonstrates the Conduktor CLI integration with token bootstrap and authentication flow',
    icon: Terminal,
    file: '/demos/cli-integration.cast',
    duration: '~30s',
    highlights: [
      'Bootstrap script authenticates to Console API',
      'Extracts Bearer token from login response',
      'Creates Kubernetes Secret with credentials',
      'Shows ConduktorCli execution with applied resources'
    ]
  },
  {
    id: 'operator-demo',
    title: 'Operator Demo',
    description: 'Shows the Kubernetes operator in action with kubectl commands and CRD management',
    icon: GitBranch,
    file: '/demos/operator-demo.cast',
    duration: '~1m',
    highlights: [
      'Apply ApplicationService, KafkaCluster, and ServiceAccount CRDs',
      'Webhook validation of ownership chains',
      'kubectl get/describe commands showing resource status',
      'Topic creation with serviceAccountRef validation'
    ]
  },
  {
    id: 'reconciliation-demo',
    title: 'Reconciliation Controller Demo',
    description: 'Demonstrates the reconciliation loop that watches CRDs and syncs to Conduktor Gateway',
    icon: RefreshCw,
    file: '/demos/reconciliation-demo.cast',
    duration: '~2m',
    highlights: [
      'ReconciliationController starts and watches CRDs',
      'Fabric8 informer detects ADD/UPDATE/DELETE events',
      'ConduktorCli transforms and applies resources',
      'Live sync from Kubernetes to Conduktor Gateway',
      '30-second resync period demonstration'
    ]
  }
]

export default function DemosPage() {
  return (
    <PageLayout
      title="Live Demos"
      subtitle="Interactive asciinema recordings of the operator in action"
      icon={Play}
      breadcrumb="Demos"
      aphorism={{
        text: "Show, don't tell.",
        author: "Anton Chekhov"
      }}
    >
      {/* Overview */}
      <Section subtitle="Watch the operator in action with real terminal recordings">
        <Card>
          <div className="prose dark:prose-invert max-w-none">
            <p>
              These asciinema recordings demonstrate the Kubernetes Messaging Operator's key features.
              Each recording is interactive - you can pause, copy text from the terminal, and control playback.
            </p>
            <p className="text-sm text-gray-600 dark:text-gray-400 mb-0">
              <strong>Tip:</strong> Click on the terminal to pause/play, use the timeline to jump to specific sections,
              and hover over text to copy commands.
            </p>
          </div>
        </Card>
      </Section>

      {/* Demos */}
      {demos.map((demo, index) => (
        <Section
          key={demo.id}
          title={demo.title}
          subtitle={demo.description}
        >
          <div className="space-y-6">
            {/* Demo Info Card */}
            <div className="grid md:grid-cols-3 gap-4">
              <Card icon={demo.icon} className="md:col-span-2">
                <div className="text-sm text-gray-600 dark:text-gray-400">
                  <div className="mb-3">
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 text-xs font-medium">
                      <Play size={12} />
                      {demo.duration}
                    </span>
                  </div>
                  <p className="mb-3">{demo.description}</p>
                  <div className="mt-4">
                    <div className="font-semibold text-gray-900 dark:text-gray-100 mb-2">What you'll see:</div>
                    <ul className="space-y-1.5 ml-4">
                      {demo.highlights.map((highlight, idx) => (
                        <li key={idx} className="text-sm">• {highlight}</li>
                      ))}
                    </ul>
                  </div>
                </div>
              </Card>

              {/* Quick Stats */}
              <div className="space-y-4">
                <Card>
                  <div className="text-center">
                    <div className="text-3xl font-bold text-blue-600 dark:text-blue-400">
                      #{index + 1}
                    </div>
                    <div className="text-sm text-gray-500 mt-1">Demo</div>
                  </div>
                </Card>
                <Card>
                  <div className="text-xs text-gray-500 uppercase tracking-wider mb-2">Controls</div>
                  <div className="text-sm space-y-1 text-gray-600 dark:text-gray-400">
                    <div>• <kbd className="px-1.5 py-0.5 text-xs bg-gray-200 dark:bg-gray-700 rounded">Space</kbd> Play/Pause</div>
                    <div>• <kbd className="px-1.5 py-0.5 text-xs bg-gray-200 dark:bg-gray-700 rounded">.</kbd> Step Forward</div>
                    <div>• <kbd className="px-1.5 py-0.5 text-xs bg-gray-200 dark:bg-gray-700 rounded">f</kbd> Fullscreen</div>
                  </div>
                </Card>
              </div>
            </div>

            {/* Asciinema Player */}
            <AsciinemaPlayer
              src={demo.file}
              title={`$ ${demo.title.toLowerCase().replace(/ /g, '-')}`}
              autoPlay={false}
              loop={false}
              speed={1}
              theme="monokai"
            />
          </div>
        </Section>
      ))}

      {/* Recording Info */}
      <Section title="About These Recordings">
        <Card>
          <div className="prose dark:prose-invert max-w-none text-sm">
            <p>
              These recordings were created using <a href="https://asciinema.org/" target="_blank" rel="noopener noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline">asciinema</a>,
              a free and open source solution for recording terminal sessions.
            </p>
            <div className="mt-4 grid md:grid-cols-2 gap-4 not-prose">
              <div className="p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
                <div className="font-semibold mb-2">Recording Commands</div>
                <code className="text-xs">
                  asciinema rec demo.cast<br/>
                  # Run your commands<br/>
                  # Press Ctrl+D to finish
                </code>
              </div>
              <div className="p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
                <div className="font-semibold mb-2">Playback</div>
                <code className="text-xs">
                  asciinema play demo.cast
                </code>
              </div>
            </div>
            <p className="text-xs text-gray-500 mt-4 mb-0">
              Source files: <code>docs/demos/*.cast</code> in the repository
            </p>
          </div>
        </Card>
      </Section>
    </PageLayout>
  )
}
