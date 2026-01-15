import { Building2, Layers, Database, Server, Shield, GitBranch } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import DiagramBox, { DiagramNode, DiagramArrow } from '../components/DiagramBox'
import Section from '../components/Section'
import CRDReferenceGraph from '../components/CRDReferenceGraph'

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
      aphorism={{
        text: "In architecture, the pride of man, his triumph over gravitation, his will to power, assume visible form. Architecture is a sort of oratory of power.",
        author: "Friedrich Nietzsche"
      }}
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
      <Section title="CRD Reference Graph" subtitle="6 Custom Resource Definitions and their reference relationships. Pan and zoom to explore.">
        <CRDReferenceGraph />

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
