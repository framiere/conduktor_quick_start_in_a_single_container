import { Link } from 'react-router-dom'
import {
  Building2, Shield, TestTube2, Code2, Server, Boxes, Network, Lock,
  ArrowRight, Zap, Users, Database, GitBranch, Layers, CheckCircle2, Quote
} from 'lucide-react'
import Card, { CardGrid } from '../components/Card'
import CodeBlock from '../components/CodeBlock'
import Section from '../components/Section'

const features = [
  {
    icon: Boxes,
    title: '6 Custom Resource Definitions',
    description: 'ApplicationService, KafkaCluster, ServiceAccount, Topic, ACL, ConsumerGroup - all managed declaratively'
  },
  {
    icon: Shield,
    title: 'Multi-Tenant Security',
    description: 'Strict ownership chains ensure resources are isolated between tenants'
  },
  {
    icon: Lock,
    title: 'Admission Control',
    description: 'ValidatingWebhook prevents invalid configurations before they reach the cluster'
  },
  {
    icon: Network,
    title: 'mTLS Authentication',
    description: 'Certificate-based identity with CN mapping to service accounts'
  },
  {
    icon: Database,
    title: 'Event-Driven Architecture',
    description: 'All operations publish reconciliation events for observability'
  },
  {
    icon: GitBranch,
    title: 'GitOps Ready',
    description: 'Fully declarative CRDs work seamlessly with ArgoCD and Flux'
  }
]

const perspectives = [
  { path: '/architecture', icon: Building2, title: 'Architecture', color: 'blue' },
  { path: '/business', icon: Users, title: 'Business', color: 'green' },
  { path: '/security', icon: Shield, title: 'Security', color: 'red' },
  { path: '/testing', icon: TestTube2, title: 'Testing', color: 'purple' },
  { path: '/developer', icon: Code2, title: 'Developer', color: 'orange' },
  { path: '/operations', icon: Server, title: 'Operations', color: 'blue' },
]

export default function HomePage() {
  return (
    <div className="animate-fade-in">
      {/* Hero */}
      <div className="bg-gradient-to-br from-gray-900 via-blue-900 to-purple-900 text-white">
        <div className="max-w-6xl mx-auto px-6 py-20 lg:py-32">
          <div className="flex items-center gap-2 text-blue-300 text-sm mb-6">
            <Zap size={16} />
            <span>Kubernetes Native Messaging Platform</span>
          </div>
          <h1 className="text-5xl lg:text-7xl font-bold mb-6">
            <span className="bg-gradient-to-r from-white via-blue-200 to-purple-200 bg-clip-text text-transparent">
              Messaging Operator
            </span>
          </h1>
          <p className="text-xl lg:text-2xl text-gray-300 max-w-3xl mb-10">
            A Kubernetes Operator for managing multi-tenant messaging infrastructure with
            Custom Resource Definitions, admission webhooks, and declarative configuration.
          </p>
          <div className="flex flex-wrap gap-4">
            <Link
              to="/architecture"
              className="inline-flex items-center gap-2 px-6 py-3 bg-white text-gray-900 rounded-full font-semibold hover:bg-gray-100 transition-colors"
            >
              Explore Architecture
              <ArrowRight size={18} />
            </Link>
            <Link
              to="/developer"
              className="inline-flex items-center gap-2 px-6 py-3 bg-white/10 text-white rounded-full font-semibold hover:bg-white/20 transition-colors backdrop-blur-sm"
            >
              Quick Start
            </Link>
          </div>
          <div className="mt-12 flex items-start gap-3 max-w-2xl">
            <Quote size={20} className="text-white/40 shrink-0 mt-1" />
            <blockquote className="text-white/80 italic text-lg leading-relaxed">
              We shape our tools, and thereafter our tools shape us.
              <footer className="mt-2 text-white/60 text-sm not-italic">
                — Marshall McLuhan
              </footer>
            </blockquote>
          </div>
        </div>
      </div>

      {/* Quick Start */}
      <div className="max-w-6xl mx-auto px-6 py-16">
        <Section title="Quick Start" subtitle="Get up and running in minutes">
          <div className="grid md:grid-cols-2 gap-8">
            <div className="space-y-6">
              <div className="flex items-start gap-4">
                <div className="w-8 h-8 rounded-full bg-blue-500 text-white flex items-center justify-center font-bold shrink-0">1</div>
                <div>
                  <h3 className="font-semibold mb-1">Clone and Build</h3>
                  <p className="text-gray-600 dark:text-gray-400 text-sm">Build the Java project with Maven</p>
                </div>
              </div>
              <div className="flex items-start gap-4">
                <div className="w-8 h-8 rounded-full bg-blue-500 text-white flex items-center justify-center font-bold shrink-0">2</div>
                <div>
                  <h3 className="font-semibold mb-1">Start Minikube</h3>
                  <p className="text-gray-600 dark:text-gray-400 text-sm">Set up a local Kubernetes cluster</p>
                </div>
              </div>
              <div className="flex items-start gap-4">
                <div className="w-8 h-8 rounded-full bg-blue-500 text-white flex items-center justify-center font-bold shrink-0">3</div>
                <div>
                  <h3 className="font-semibold mb-1">Deploy Operator</h3>
                  <p className="text-gray-600 dark:text-gray-400 text-sm">Install with Helm chart</p>
                </div>
              </div>
              <div className="flex items-start gap-4">
                <div className="w-8 h-8 rounded-full bg-green-500 text-white flex items-center justify-center font-bold shrink-0">
                  <CheckCircle2 size={18} />
                </div>
                <div>
                  <h3 className="font-semibold mb-1">Create Resources</h3>
                  <p className="text-gray-600 dark:text-gray-400 text-sm">Apply your first CRDs</p>
                </div>
              </div>
            </div>
            <CodeBlock
              title="Terminal"
              code={`# Build the project
mvn clean package -DskipTests

# Start Minikube cluster
./functional-tests/setup-minikube.sh

# Deploy operator
./functional-tests/deploy.sh

# Create an ApplicationService
kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: my-app
spec:
  name: my-app
EOF`}
            />
          </div>
        </Section>

        {/* Features */}
        <Section title="Key Features" subtitle="Everything you need for multi-tenant messaging">
          <CardGrid cols={3}>
            {features.map((feature, index) => {
              const Icon = feature.icon
              return (
                <Card key={index}>
                  <div className="p-3 bg-blue-100 dark:bg-blue-900/30 rounded-xl w-fit mb-4">
                    <Icon className="text-blue-600 dark:text-blue-400" size={24} />
                  </div>
                  <h3 className="font-semibold mb-2">{feature.title}</h3>
                  <p className="text-gray-600 dark:text-gray-400 text-sm">{feature.description}</p>
                </Card>
              )
            })}
          </CardGrid>
        </Section>

        {/* Perspectives */}
        <Section title="Explore by Perspective" subtitle="Understanding the system from different viewpoints">
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            {perspectives.map((p) => {
              const Icon = p.icon
              return (
                <Link
                  key={p.path}
                  to={p.path}
                  className="group flex flex-col items-center p-6 bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 hover:border-blue-500 dark:hover:border-blue-500 transition-colors"
                >
                  <div className="p-3 bg-gray-100 dark:bg-gray-800 rounded-xl group-hover:bg-blue-100 dark:group-hover:bg-blue-900/30 transition-colors mb-3">
                    <Icon className="text-gray-600 dark:text-gray-400 group-hover:text-blue-600 dark:group-hover:text-blue-400" size={24} />
                  </div>
                  <span className="font-medium text-sm">{p.title}</span>
                </Link>
              )
            })}
          </div>
        </Section>

        {/* CRD Hierarchy Preview */}
        <Section title="CRD Hierarchy" subtitle="Strict ownership chain for multi-tenant isolation">
          <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 p-8">
            <div className="flex flex-col items-center space-y-4">
              {/* Root */}
              <div className="px-6 py-3 bg-blue-100 dark:bg-blue-900/50 border-2 border-blue-300 dark:border-blue-700 rounded-xl font-semibold text-blue-800 dark:text-blue-200">
                ApplicationService
              </div>
              <div className="text-gray-400 text-2xl">↓</div>

              {/* Level 2 */}
              <div className="flex items-center gap-8">
                <div className="px-6 py-3 bg-purple-100 dark:bg-purple-900/50 border-2 border-purple-300 dark:border-purple-700 rounded-xl font-semibold text-purple-800 dark:text-purple-200">
                  KafkaCluster
                </div>
                <div className="px-6 py-3 bg-green-100 dark:bg-green-900/50 border-2 border-green-300 dark:border-green-700 rounded-xl font-semibold text-green-800 dark:text-green-200">
                  ServiceAccount
                </div>
              </div>
              <div className="text-gray-400 text-2xl">↓</div>

              {/* Level 3 */}
              <div className="flex items-center gap-8">
                <div className="px-6 py-3 bg-orange-100 dark:bg-orange-900/50 border-2 border-orange-300 dark:border-orange-700 rounded-xl font-semibold text-orange-800 dark:text-orange-200">
                  Topic
                </div>
                <div className="px-6 py-3 bg-red-100 dark:bg-red-900/50 border-2 border-red-300 dark:border-red-700 rounded-xl font-semibold text-red-800 dark:text-red-200">
                  ACL
                </div>
                <div className="px-6 py-3 bg-gray-100 dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-600 rounded-xl font-semibold text-gray-800 dark:text-gray-200">
                  ConsumerGroup
                </div>
              </div>
            </div>
            <p className="text-center text-gray-500 dark:text-gray-400 mt-8 text-sm">
              All resources must reference the same ApplicationService — enforced by the ValidatingWebhook
            </p>
          </div>
        </Section>

        {/* Tech Stack */}
        <Section title="Technology Stack">
          <div className="flex flex-wrap gap-3">
            {['Java 21', 'Maven', 'Kubernetes', 'Helm 3', 'Fabric8 Client', 'JUnit 5', 'AssertJ', 'Bats', 'Minikube', 'mTLS'].map((tech) => (
              <span key={tech} className="px-4 py-2 bg-gray-100 dark:bg-gray-800 rounded-full text-sm font-medium">
                {tech}
              </span>
            ))}
          </div>
        </Section>
      </div>
    </div>
  )
}
