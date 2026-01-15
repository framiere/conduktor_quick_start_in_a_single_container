import { Shield, Lock, Key, UserCheck, AlertTriangle, CheckCircle2, XCircle, FileKey } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import DiagramBox, { DiagramNode, DiagramArrow } from '../components/DiagramBox'
import Section from '../components/Section'
import CodeBlock from '../components/CodeBlock'

const securityLayers = [
  {
    icon: Lock,
    title: 'mTLS Authentication',
    description: 'All client connections require valid certificates. The CN field identifies the service account.',
    details: ['Certificate-based identity', 'No passwords transmitted', 'Mutual verification']
  },
  {
    icon: Shield,
    title: 'Ownership Chains',
    description: 'Resources must belong to the same ApplicationService. Cross-tenant access is structurally impossible.',
    details: ['Enforced at admission time', 'Immutable after creation', 'Hierarchical validation']
  },
  {
    icon: UserCheck,
    title: 'Admission Control',
    description: 'ValidatingWebhook rejects invalid configurations before they reach the cluster.',
    details: ['Synchronous validation', 'Clear error messages', 'No invalid state possible']
  },
  {
    icon: Key,
    title: 'ACL Enforcement',
    description: 'Fine-grained permissions control which operations are allowed on which resources.',
    details: ['Topic-level permissions', 'Operation types (READ/WRITE)', 'Pattern matching support']
  }
]

const mtlsExamples = [
  { cn: 'demo-admin', cluster: 'demo', role: 'Admin (ACL disabled)', color: 'blue' },
  { cn: 'demo-acl-admin', cluster: 'demo-acl', role: 'Superuser', color: 'green' },
  { cn: 'demo-acl-user', cluster: 'demo-acl', role: 'Restricted by ACL', color: 'orange' }
]

const validationRules = [
  {
    rule: 'Same Owner Validation',
    description: 'Referenced resources must belong to the same ApplicationService',
    example: 'Topic references ServiceAccount → both must have same applicationServiceRef',
    passes: true
  },
  {
    rule: 'Ownership Immutability',
    description: 'applicationServiceRef cannot be changed after creation',
    example: 'Updating a Topic to change its applicationServiceRef',
    passes: false
  },
  {
    rule: 'Reference Existence',
    description: 'Referenced resources must exist before referencing them',
    example: 'Creating Topic that references non-existent ServiceAccount',
    passes: false
  },
  {
    rule: 'Circular Reference Prevention',
    description: 'Resources cannot create circular dependency chains',
    example: 'ACL → Topic → ACL circular reference',
    passes: false
  }
]

export default function SecurityPage() {
  return (
    <PageLayout
      title="Security"
      subtitle="Multi-tenant isolation, mTLS authentication, and access control"
      icon={Shield}
      breadcrumb="Security"
      aphorism={{
        text: "The only truly secure system is one that is powered off, cast in a block of concrete and sealed in a lead-lined room with armed guards.",
        author: "Gene Spafford"
      }}
    >
      {/* Security Layers */}
      <Section title="Security Layers" subtitle="Defense in depth approach">
        <CardGrid cols={2}>
          {securityLayers.map((layer, index) => {
            const Icon = layer.icon
            return (
              <Card key={index}>
                <div className="flex items-start gap-4">
                  <div className="p-3 bg-red-100 dark:bg-red-900/30 rounded-xl shrink-0">
                    <Icon className="text-red-600 dark:text-red-400" size={24} />
                  </div>
                  <div>
                    <h3 className="font-semibold mb-2">{layer.title}</h3>
                    <p className="text-gray-600 dark:text-gray-400 text-sm mb-3">{layer.description}</p>
                    <ul className="space-y-1">
                      {layer.details.map((detail, i) => (
                        <li key={i} className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
                          <CheckCircle2 size={14} className="text-green-500" />
                          {detail}
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              </Card>
            )
          })}
        </CardGrid>
      </Section>

      {/* mTLS Flow */}
      <Section title="mTLS Authentication Flow" subtitle="Certificate-based identity mapping">
        <DiagramBox title="Client Certificate to Service Account Mapping">
          <div className="flex flex-col items-center py-6 space-y-4">
            {/* Client */}
            <DiagramNode color="gray" className="w-64">
              <div className="font-bold">Kafka Client</div>
              <div className="text-xs opacity-70 mt-1">Presents client certificate</div>
            </DiagramNode>

            <DiagramArrow direction="down" label="TLS handshake with client cert" />

            {/* Gateway */}
            <DiagramNode color="purple" className="w-64">
              <div className="font-bold">Conduktor Gateway</div>
              <div className="text-xs opacity-70 mt-1">Extracts CN from certificate</div>
            </DiagramNode>

            <DiagramArrow direction="down" label="CN → ServiceAccount lookup" />

            {/* Service Account */}
            <DiagramNode color="green" className="w-64">
              <div className="font-bold">ServiceAccount CRD</div>
              <div className="text-xs opacity-70 mt-1">Maps to KafkaCluster + ACLs</div>
            </DiagramNode>

            <DiagramArrow direction="down" label="Apply ACL rules" />

            {/* Topic Access */}
            <DiagramNode color="blue" className="w-64">
              <div className="font-bold">Topic Access</div>
              <div className="text-xs opacity-70 mt-1">Allowed or denied based on ACLs</div>
            </DiagramNode>
          </div>
        </DiagramBox>

        {/* CN Mapping Examples */}
        <div className="mt-6">
          <h4 className="font-semibold mb-4">CN Mapping Examples</h4>
          <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 overflow-hidden">
            <table className="w-full">
              <thead className="bg-gray-50 dark:bg-gray-800">
                <tr>
                  <th className="px-6 py-3 text-left text-sm font-semibold">Certificate CN</th>
                  <th className="px-6 py-3 text-left text-sm font-semibold">Virtual Cluster</th>
                  <th className="px-6 py-3 text-left text-sm font-semibold">Role</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
                {mtlsExamples.map((ex, index) => (
                  <tr key={index}>
                    <td className="px-6 py-4">
                      <code className="text-sm bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded">CN={ex.cn}</code>
                    </td>
                    <td className="px-6 py-4 text-gray-600 dark:text-gray-400">{ex.cluster}</td>
                    <td className="px-6 py-4 text-gray-600 dark:text-gray-400">{ex.role}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </Section>

      {/* Ownership Chain Validation */}
      <Section title="Ownership Chain Validation" subtitle="Rules enforced by the ValidatingWebhook">
        <div className="space-y-4">
          {validationRules.map((rule, index) => (
            <Card key={index} variant={rule.passes ? 'success' : 'error'}>
              <div className="flex items-start gap-4">
                <div className={`p-2 rounded-lg ${rule.passes ? 'bg-green-100 dark:bg-green-900/30' : 'bg-red-100 dark:bg-red-900/30'}`}>
                  {rule.passes
                    ? <CheckCircle2 className="text-green-600 dark:text-green-400" size={20} />
                    : <XCircle className="text-red-600 dark:text-red-400" size={20} />
                  }
                </div>
                <div className="flex-1">
                  <h3 className="font-semibold mb-1">{rule.rule}</h3>
                  <p className="text-gray-600 dark:text-gray-400 text-sm mb-2">{rule.description}</p>
                  <div className="flex items-center gap-2 text-sm">
                    <span className="text-gray-500">Example:</span>
                    <code className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-xs">{rule.example}</code>
                    <span className={rule.passes ? 'text-green-600' : 'text-red-600'}>
                      {rule.passes ? '✓ Allowed' : '✗ Rejected'}
                    </span>
                  </div>
                </div>
              </div>
            </Card>
          ))}
        </div>
      </Section>

      {/* Example: Valid vs Invalid */}
      <Section title="Validation Examples" subtitle="What passes and what gets rejected">
        <div className="grid md:grid-cols-2 gap-6">
          <div>
            <h4 className="font-semibold text-green-600 dark:text-green-400 mb-3 flex items-center gap-2">
              <CheckCircle2 size={18} />
              Valid Configuration
            </h4>
            <CodeBlock
              title="topic.yaml"
              language="yaml"
              code={`apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: orders-topic
spec:
  name: orders
  # ✓ Same owner as ServiceAccount
  applicationServiceRef: team-a-app
  # ✓ ServiceAccount exists and has same owner
  serviceRef: team-a-service-account
  partitions: 3
  replicationFactor: 1`}
            />
          </div>
          <div>
            <h4 className="font-semibold text-red-600 dark:text-red-400 mb-3 flex items-center gap-2">
              <XCircle size={18} />
              Invalid Configuration
            </h4>
            <CodeBlock
              title="topic.yaml"
              language="yaml"
              code={`apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: orders-topic
spec:
  name: orders
  # ✗ Different owner!
  applicationServiceRef: team-a-app
  # ✗ ServiceAccount belongs to team-b
  serviceRef: team-b-service-account
  partitions: 3
  replicationFactor: 1

# ERROR: serviceRef team-b-service-account
# has different applicationServiceRef`}
            />
          </div>
        </div>
      </Section>

      {/* Security Checklist */}
      <Section title="Security Checklist">
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 p-6">
          <ul className="space-y-3">
            {[
              'All connections use mTLS with valid certificates',
              'Certificate CNs map to ServiceAccount resources',
              'ApplicationService creates tenant boundaries',
              'Ownership chains prevent cross-tenant access',
              'ValidatingWebhook rejects invalid configurations synchronously',
              'ACLs enforce fine-grained topic-level permissions',
              'All changes tracked through GitOps workflow',
              'Event stream provides audit trail'
            ].map((item, index) => (
              <li key={index} className="flex items-center gap-3">
                <CheckCircle2 className="text-green-500 shrink-0" size={20} />
                <span className="text-gray-700 dark:text-gray-300">{item}</span>
              </li>
            ))}
          </ul>
        </div>
      </Section>
    </PageLayout>
  )
}
