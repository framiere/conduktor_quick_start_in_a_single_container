import { Building2, Layers, Database, Server, Shield, GitBranch } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import DiagramBox, { DiagramNode, DiagramArrow } from '../components/DiagramBox'
import Section from '../components/Section'

const packages = [
  { name: 'crd', description: 'Custom Resource Definition classes with Fabric8 annotations', color: 'blue' },
  { name: 'validation', description: 'OwnershipValidator enforces ownership chains, ValidationResult wraps outcomes', color: 'green' },
  { name: 'webhook', description: 'Kubernetes ValidatingWebhook implementation using Java HttpServer (port 8443)', color: 'purple' },
  { name: 'store', description: 'CRDStore is thread-safe in-memory storage with event publishing', color: 'orange' },
  { name: 'events', description: 'ReconciliationEvent publishes BEFORE/AFTER events for CREATE/UPDATE/DELETE', color: 'red' },
]

export default function ArchitecturePage() {
  return (
    <PageLayout
      title="Architecture"
      subtitle="Deep dive into system design, components, and how they interact"
      icon={Building2}
      breadcrumb="Architecture"
    >
      {/* High-Level Overview */}
      <Section title="System Overview" subtitle="How the operator fits into the Kubernetes ecosystem">
        <DiagramBox title="High-Level Architecture">
          <div className="flex flex-col lg:flex-row items-center justify-center gap-8 py-4">
            {/* Client Side */}
            <div className="flex flex-col items-center gap-4">
              <DiagramNode color="gray">kubectl / GitOps</DiagramNode>
              <DiagramArrow direction="down" label="YAML manifests" />
            </div>

            {/* Kubernetes API */}
            <div className="flex flex-col items-center gap-4">
              <DiagramNode color="blue">Kubernetes API Server</DiagramNode>
              <div className="flex gap-8">
                <div className="flex flex-col items-center gap-4">
                  <DiagramArrow direction="down" label="admission review" />
                  <DiagramNode color="purple">ValidatingWebhook</DiagramNode>
                </div>
                <div className="flex flex-col items-center gap-4">
                  <DiagramArrow direction="down" label="watch/reconcile" />
                  <DiagramNode color="green">Operator Controller</DiagramNode>
                </div>
              </div>
            </div>

            {/* Backend */}
            <div className="flex flex-col items-center gap-4">
              <DiagramNode color="orange">Messaging Platform</DiagramNode>
              <span className="text-sm text-gray-500">(Conduktor Gateway)</span>
            </div>
          </div>
        </DiagramBox>
      </Section>

      {/* Package Structure */}
      <Section title="Package Structure" subtitle="com.example.messaging.operator">
        <CardGrid cols={2}>
          {packages.map((pkg) => (
            <Card key={pkg.name} variant="default">
              <div className="flex items-center gap-3 mb-2">
                <code className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-sm font-mono">
                  {pkg.name}/
                </code>
              </div>
              <p className="text-gray-600 dark:text-gray-400 text-sm">{pkg.description}</p>
            </Card>
          ))}
        </CardGrid>
      </Section>

      {/* CRD References */}
      <Section title="CRD Reference Graph" subtitle="6 Custom Resource Definitions and their reference relationships">
        <DiagramBox title="CRD Reference Graph">
          <div className="py-4">
            {/* Full SVG Graph */}
            <svg viewBox="0 0 900 520" className="w-full h-auto" style={{minHeight: '500px'}}>
              <defs>
                <marker id="arrow-blue" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                  <polygon points="0 0, 10 3.5, 0 7" fill="#3B82F6" />
                </marker>
                <marker id="arrow-purple" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                  <polygon points="0 0, 10 3.5, 0 7" fill="#8B5CF6" />
                </marker>
                <marker id="arrow-green" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                  <polygon points="0 0, 10 3.5, 0 7" fill="#10B981" />
                </marker>
                <marker id="arrow-orange" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                  <polygon points="0 0, 10 3.5, 0 7" fill="#F59E0B" />
                </marker>
                <marker id="arrow-gray" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                  <polygon points="0 0, 10 3.5, 0 7" fill="#6B7280" />
                </marker>
                <marker id="arrow-red" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                  <polygon points="0 0, 10 3.5, 0 7" fill="#EF4444" />
                </marker>
              </defs>

              {/* === NODES === */}

              {/* ApplicationService - Top Center (Root) */}
              <g transform="translate(450, 40)">
                <rect x="-90" y="-25" width="180" height="50" rx="8" fill="#3B82F6" />
                <text x="0" y="5" textAnchor="middle" fill="white" fontWeight="bold" fontSize="14">ApplicationService</text>
                <text x="0" y="-35" textAnchor="middle" fill="#6B7280" fontSize="11">ROOT</text>
              </g>

              {/* KafkaCluster - Left Upper */}
              <g transform="translate(150, 160)">
                <rect x="-75" y="-25" width="150" height="50" rx="8" fill="#8B5CF6" />
                <text x="0" y="5" textAnchor="middle" fill="white" fontWeight="bold" fontSize="14">KafkaCluster</text>
              </g>

              {/* ServiceAccount - Right Upper */}
              <g transform="translate(600, 160)">
                <rect x="-80" y="-25" width="160" height="50" rx="8" fill="#10B981" />
                <text x="0" y="5" textAnchor="middle" fill="white" fontWeight="bold" fontSize="14">ServiceAccount</text>
              </g>

              {/* Topic - Left Bottom */}
              <g transform="translate(120, 340)">
                <rect x="-55" y="-25" width="110" height="50" rx="8" fill="#F59E0B" />
                <text x="0" y="5" textAnchor="middle" fill="white" fontWeight="bold" fontSize="14">Topic</text>
              </g>

              {/* ConsumerGroup - Center Bottom */}
              <g transform="translate(450, 340)">
                <rect x="-80" y="-25" width="160" height="50" rx="8" fill="#6B7280" />
                <text x="0" y="5" textAnchor="middle" fill="white" fontWeight="bold" fontSize="14">ConsumerGroup</text>
              </g>

              {/* ACL - Right Bottom */}
              <g transform="translate(750, 340)">
                <rect x="-45" y="-25" width="90" height="50" rx="8" fill="#EF4444" />
                <text x="0" y="5" textAnchor="middle" fill="white" fontWeight="bold" fontSize="14">ACL</text>
              </g>

              {/* === ARROWS WITH LABELS === */}

              {/* KafkaCluster → ApplicationService (applicationServiceRef) */}
              <line x1="190" y1="135" x2="380" y2="65" stroke="#8B5CF6" strokeWidth="2" markerEnd="url(#arrow-purple)" />
              <text x="270" y="90" fill="#8B5CF6" fontSize="10" fontStyle="italic">applicationServiceRef</text>

              {/* ServiceAccount → ApplicationService (applicationServiceRef) */}
              <line x1="560" y1="135" x2="490" y2="65" stroke="#10B981" strokeWidth="2" markerEnd="url(#arrow-green)" />
              <text x="540" y="90" fill="#10B981" fontSize="10" fontStyle="italic">applicationServiceRef</text>

              {/* ServiceAccount → KafkaCluster (clusterRef) */}
              <line x1="520" y1="160" x2="230" y2="160" stroke="#10B981" strokeWidth="2" markerEnd="url(#arrow-green)" />
              <text x="375" y="152" fill="#10B981" fontSize="10" fontStyle="italic">clusterRef</text>

              {/* Topic → ServiceAccount (serviceRef) */}
              <line x1="175" y1="330" x2="520" y2="185" stroke="#F59E0B" strokeWidth="2" markerEnd="url(#arrow-orange)" />
              <text x="320" y="240" fill="#F59E0B" fontSize="10" fontStyle="italic">serviceRef</text>

              {/* Topic → ApplicationService (applicationServiceRef) - dashed */}
              <line x1="120" y1="315" x2="380" y2="65" stroke="#F59E0B" strokeWidth="1.5" strokeDasharray="6,3" markerEnd="url(#arrow-orange)" />
              <text x="200" y="175" fill="#F59E0B" fontSize="9" fontStyle="italic">applicationServiceRef</text>

              {/* ConsumerGroup → ServiceAccount (serviceRef) */}
              <line x1="510" y1="315" x2="580" y2="185" stroke="#6B7280" strokeWidth="2" markerEnd="url(#arrow-gray)" />
              <text x="560" y="255" fill="#6B7280" fontSize="10" fontStyle="italic">serviceRef</text>

              {/* ConsumerGroup → ApplicationService (applicationServiceRef) - dashed */}
              <line x1="450" y1="315" x2="450" y2="65" stroke="#6B7280" strokeWidth="1.5" strokeDasharray="6,3" markerEnd="url(#arrow-gray)" />
              <text x="455" y="200" fill="#6B7280" fontSize="9" fontStyle="italic">applicationServiceRef</text>

              {/* ACL → ServiceAccount (serviceRef) */}
              <line x1="720" y1="315" x2="650" y2="185" stroke="#EF4444" strokeWidth="2" markerEnd="url(#arrow-red)" />
              <text x="700" y="255" fill="#EF4444" fontSize="10" fontStyle="italic">serviceRef</text>

              {/* ACL → Topic (topicRef) - optional dashed */}
              <line x1="705" y1="350" x2="175" y2="350" stroke="#EF4444" strokeWidth="1.5" strokeDasharray="6,3" markerEnd="url(#arrow-red)" />
              <text x="430" y="365" fill="#EF4444" fontSize="9" fontStyle="italic">topicRef (optional)</text>

              {/* ACL → ConsumerGroup (consumerGroupRef) - optional dashed */}
              <line x1="705" y1="370" x2="530" y2="370" stroke="#EF4444" strokeWidth="1.5" strokeDasharray="6,3" markerEnd="url(#arrow-red)" />
              <text x="610" y="385" fill="#EF4444" fontSize="9" fontStyle="italic">consumerGroupRef (optional)</text>

              {/* ACL → ApplicationService (applicationServiceRef) - dashed */}
              <line x1="780" y1="315" x2="530" y2="65" stroke="#EF4444" strokeWidth="1.5" strokeDasharray="6,3" markerEnd="url(#arrow-red)" />
              <text x="680" y="175" fill="#EF4444" fontSize="9" fontStyle="italic">applicationServiceRef</text>

              {/* === LEGEND === */}
              <g transform="translate(30, 440)">
                <text x="0" y="0" fill="#374151" fontWeight="bold" fontSize="12">Legend:</text>
                <line x1="0" y1="20" x2="40" y2="20" stroke="#374151" strokeWidth="2" />
                <text x="50" y="24" fill="#6B7280" fontSize="11">Required reference</text>
                <line x1="180" y1="20" x2="220" y2="20" stroke="#374151" strokeWidth="1.5" strokeDasharray="6,3" />
                <text x="230" y="24" fill="#6B7280" fontSize="11">Optional reference</text>
                <text x="400" y="24" fill="#6B7280" fontSize="11">All CRDs except ApplicationService have applicationServiceRef (ownership)</text>
              </g>
            </svg>
          </div>
        </DiagramBox>

        {/* Reference Table */}
        <div className="mt-6 bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 dark:bg-gray-800">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold">CRD</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">References</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">Points To</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
              <tr>
                <td className="px-6 py-3 font-medium">ApplicationService</td>
                <td className="px-6 py-3 text-gray-500">—</td>
                <td className="px-6 py-3 text-gray-500">Root resource</td>
              </tr>
              <tr>
                <td className="px-6 py-3 font-medium">KafkaCluster</td>
                <td className="px-6 py-3"><code className="text-sm">applicationServiceRef</code></td>
                <td className="px-6 py-3">ApplicationService</td>
              </tr>
              <tr>
                <td className="px-6 py-3 font-medium">ServiceAccount</td>
                <td className="px-6 py-3"><code className="text-sm">clusterRef</code>, <code className="text-sm">applicationServiceRef</code></td>
                <td className="px-6 py-3">KafkaCluster, ApplicationService</td>
              </tr>
              <tr>
                <td className="px-6 py-3 font-medium">Topic</td>
                <td className="px-6 py-3"><code className="text-sm">serviceRef</code>, <code className="text-sm">applicationServiceRef</code></td>
                <td className="px-6 py-3">ServiceAccount, ApplicationService</td>
              </tr>
              <tr>
                <td className="px-6 py-3 font-medium">ConsumerGroup</td>
                <td className="px-6 py-3"><code className="text-sm">serviceRef</code>, <code className="text-sm">applicationServiceRef</code></td>
                <td className="px-6 py-3">ServiceAccount, ApplicationService</td>
              </tr>
              <tr>
                <td className="px-6 py-3 font-medium">ACL</td>
                <td className="px-6 py-3"><code className="text-sm">serviceRef</code>, <code className="text-sm">topicRef?</code>, <code className="text-sm">consumerGroupRef?</code></td>
                <td className="px-6 py-3">ServiceAccount, Topic (opt), ConsumerGroup (opt)</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="mt-6 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-xl border border-blue-200 dark:border-blue-800">
          <p className="text-sm text-blue-800 dark:text-blue-200">
            <strong>Validation Rule:</strong> All referenced resources must exist and belong to the same <code>applicationServiceRef</code>.
            The ValidatingWebhook enforces this at admission time, preventing cross-tenant access.
          </p>
        </div>
      </Section>

      {/* Design Decisions */}
      <Section title="Key Design Decisions">
        <CardGrid cols={2}>
          <Card icon={Layers} title="CRDKind Enum">
            <p className="text-gray-600 dark:text-gray-400 text-sm">
              Type-safe CRD kind handling using an enum instead of raw strings.
              Maps kind names to their Java classes for compile-time safety.
            </p>
          </Card>
          <Card icon={Shield} title="Ownership Immutability">
            <p className="text-gray-600 dark:text-gray-400 text-sm">
              The applicationServiceRef field cannot be changed after creation.
              This prevents resources from being "moved" between tenants.
            </p>
          </Card>
          <Card icon={Database} title="Reference Validation">
            <p className="text-gray-600 dark:text-gray-400 text-sm">
              Referenced resources must exist and belong to the same owner.
              The webhook validates all cross-references at admission time.
            </p>
          </Card>
          <Card icon={GitBranch} title="Event-Driven Observability">
            <p className="text-gray-600 dark:text-gray-400 text-sm">
              All store operations publish reconciliation events (BEFORE/AFTER).
              Enables audit logging and integration with external systems.
            </p>
          </Card>
        </CardGrid>
      </Section>

      {/* Webhook Endpoints */}
      <Section title="Webhook Endpoints" subtitle="HTTPS endpoints exposed by the webhook server">
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 dark:bg-gray-800">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold">Endpoint</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">Description</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
              <tr>
                <td className="px-6 py-4"><code className="text-sm">/health</code></td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Health check endpoint</td>
              </tr>
              <tr>
                <td className="px-6 py-4"><code className="text-sm">/validate/topic</code></td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Topic admission validation</td>
              </tr>
              <tr>
                <td className="px-6 py-4"><code className="text-sm">/validate/acl</code></td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">ACL admission validation</td>
              </tr>
              <tr>
                <td className="px-6 py-4"><code className="text-sm">/validate/serviceaccount</code></td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">ServiceAccount admission validation</td>
              </tr>
              <tr>
                <td className="px-6 py-4"><code className="text-sm">/validate/kafkacluster</code></td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">KafkaCluster admission validation</td>
              </tr>
              <tr>
                <td className="px-6 py-4"><code className="text-sm">/validate/consumergroup</code></td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">ConsumerGroup admission validation</td>
              </tr>
            </tbody>
          </table>
        </div>
      </Section>
    </PageLayout>
  )
}
