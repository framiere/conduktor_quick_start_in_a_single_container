import { Activity, Search, FileText, CheckCircle2, XCircle, Filter } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import Section from '../components/Section'
import CodeBlock from '../components/CodeBlock'
import DiagramBox, { DiagramNode, DiagramArrow } from '../components/DiagramBox'

const eventTypes = [
  { type: 'RECONCILIATION_START', description: 'Operation begins', color: 'blue', icon: Activity },
  { type: 'RECONCILIATION_END', description: 'Operation completes', color: 'green', icon: CheckCircle2 },
  { type: 'SUCCESS', description: 'Operation successful', color: 'green', icon: CheckCircle2 },
  { type: 'FAILED', description: 'Operation failed', color: 'red', icon: XCircle },
]

const sampleLogs = `[main] INFO ReconciliationEventPublisher - RECONCILIATION_START: CREATE ApplicationService default/team-a-app
[main] INFO ReconciliationEventPublisher - RECONCILIATION_END: CREATE ApplicationService default/team-a-app - SUCCESS CREATE completed successfully
[main] INFO ReconciliationEventPublisher - RECONCILIATION_START: CREATE KafkaCluster default/team-a-cluster
[main] INFO ReconciliationEventPublisher - RECONCILIATION_END: CREATE KafkaCluster default/team-a-cluster - SUCCESS CREATE completed successfully
[main] INFO ReconciliationEventPublisher - RECONCILIATION_START: CREATE ServiceAccount default/team-a-sa
[main] INFO ReconciliationEventPublisher - RECONCILIATION_END: CREATE ServiceAccount default/team-a-sa - SUCCESS CREATE completed successfully
[main] INFO ReconciliationEventPublisher - RECONCILIATION_START: CREATE Topic default/orders-events
[main] INFO ReconciliationEventPublisher - RECONCILIATION_END: CREATE Topic default/orders-events - SUCCESS CREATE completed successfully
[main] INFO ReconciliationEventPublisher - RECONCILIATION_START: CREATE ACL default/orders-acl
[main] INFO ReconciliationEventPublisher - RECONCILIATION_END: CREATE ACL default/orders-acl - SUCCESS CREATE completed successfully`

const failedLog = `[main] INFO ReconciliationEventPublisher - RECONCILIATION_START: CREATE Topic default/cross-tenant-topic
[main] INFO ReconciliationEventPublisher - RECONCILIATION_END: CREATE Topic default/cross-tenant-topic - FAILED Validation failed: Referenced ServiceAccount 'other-team-sa' has different applicationServiceRef`

const kubeEventExamples = [
  { query: 'kubectl get events --field-selector involvedObject.name=team-a-app', description: 'Get events for specific resource' },
  { query: 'kubectl get events --field-selector reason=ReconciliationFailed', description: 'Find all failed reconciliations' },
  { query: 'kubectl get events --field-selector involvedObject.kind=Topic', description: 'Find all topic-related events' },
  { query: 'kubectl get events -w', description: 'Watch events in real-time' },
]

const eventFields = [
  { field: 'timestamp', description: 'When the event occurred', example: '2024-01-15T09:15:23.456Z' },
  { field: 'operation', description: 'CREATE, UPDATE, or DELETE', example: 'CREATE' },
  { field: 'resourceKind', description: 'Type of CRD resource', example: 'Topic' },
  { field: 'namespace', description: 'Kubernetes namespace', example: 'default' },
  { field: 'name', description: 'Resource name', example: 'orders-events' },
  { field: 'status', description: 'SUCCESS or FAILED', example: 'SUCCESS' },
  { field: 'message', description: 'Result description', example: 'CREATE completed successfully' },
]

export default function OperabilityPage() {
  return (
    <PageLayout
      title="Operability"
      subtitle="Complete visibility into every operation through structured event logging"
      icon={Activity}
      breadcrumb="Operability"
      aphorism={{
        text: "What gets measured gets managed.",
        author: "Peter Drucker"
      }}
    >
      {/* Overview */}
      <Section title="Full Visibility" subtitle="Every operation is tracked and logged">
        <Card>
          <div className="flex items-start gap-4">
            <div className="p-3 bg-blue-100 dark:bg-blue-900/30 rounded-xl shrink-0">
              <Activity className="text-blue-600 dark:text-blue-400" size={28} />
            </div>
            <div>
              <h3 className="font-semibold text-lg mb-2">Event-Driven Architecture</h3>
              <p className="text-gray-600 dark:text-gray-400">
                The operator publishes structured events for every operation. Each CREATE, UPDATE, and DELETE
                is logged with full context including the resource type, namespace, name, and outcome.
                This provides a complete audit trail that can be searched, filtered, and analyzed.
              </p>
            </div>
          </div>
        </Card>
      </Section>

      {/* Event Flow */}
      <Section title="Event Lifecycle" subtitle="How operations are tracked from start to finish">
        <DiagramBox title="Operation Event Flow">
          <div className="py-8 flex flex-col items-center space-y-6">
            <div className="flex items-center gap-6">
              <DiagramNode color="gray" className="w-48">
                kubectl apply
              </DiagramNode>
              <DiagramArrow direction="right" />
              <DiagramNode color="blue" className="w-56">
                <div className="flex items-center gap-2 justify-center">
                  <Activity size={18} />
                  <span>RECONCILIATION_START</span>
                </div>
              </DiagramNode>
            </div>

            <DiagramArrow direction="down" label="Processing" />

            <div className="flex items-center gap-8">
              <div className="flex flex-col items-center gap-2">
                <DiagramNode color="green" className="w-48">
                  <div className="flex items-center gap-2 justify-center">
                    <CheckCircle2 size={18} />
                    <span>SUCCESS</span>
                  </div>
                </DiagramNode>
                <span className="text-xs text-gray-500">Resource created/updated</span>
              </div>
              <span className="text-gray-400">or</span>
              <div className="flex flex-col items-center gap-2">
                <DiagramNode color="red" className="w-48">
                  <div className="flex items-center gap-2 justify-center">
                    <XCircle size={18} />
                    <span>FAILED</span>
                  </div>
                </DiagramNode>
                <span className="text-xs text-gray-500">With error message</span>
              </div>
            </div>

            <DiagramArrow direction="down" />

            <DiagramNode color="purple" className="w-56">
              <div className="flex items-center gap-2 justify-center">
                <FileText size={18} />
                <span>RECONCILIATION_END</span>
              </div>
            </DiagramNode>
          </div>
        </DiagramBox>
      </Section>

      {/* Event Types */}
      <Section title="Event Types" subtitle="Structured events for every state change">
        <CardGrid cols={2}>
          {eventTypes.map((event, index) => {
            const Icon = event.icon
            const colorClasses = {
              blue: 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400',
              green: 'bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400',
              red: 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400',
            }
            return (
              <Card key={index}>
                <div className="flex items-center gap-4">
                  <div className={`p-3 rounded-xl ${colorClasses[event.color]}`}>
                    <Icon size={24} />
                  </div>
                  <div>
                    <code className="text-sm font-mono bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded">
                      {event.type}
                    </code>
                    <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{event.description}</p>
                  </div>
                </div>
              </Card>
            )
          })}
        </CardGrid>
      </Section>

      {/* Event Fields */}
      <Section title="Event Data" subtitle="Information captured in each event">
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 dark:bg-gray-800">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold">Field</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">Description</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">Example</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
              {eventFields.map((field, index) => (
                <tr key={index}>
                  <td className="px-6 py-4">
                    <code className="text-sm bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded">
                      {field.field}
                    </code>
                  </td>
                  <td className="px-6 py-4 text-gray-600 dark:text-gray-400">{field.description}</td>
                  <td className="px-6 py-4">
                    <code className="text-sm text-blue-600 dark:text-blue-400">{field.example}</code>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      {/* Sample Logs */}
      <Section title="Sample Log Output" subtitle="What the logs look like in practice">
        <Card>
          <div className="flex items-center gap-2 mb-4">
            <div className="p-2 bg-green-100 dark:bg-green-900/30 rounded-lg">
              <CheckCircle2 className="text-green-600 dark:text-green-400" size={18} />
            </div>
            <h4 className="font-semibold">Successful Tenant Setup</h4>
          </div>
          <CodeBlock code={sampleLogs} />
          <p className="text-sm text-gray-500 mt-4">
            Each step in the ownership chain is tracked: ApplicationService → KafkaCluster → ServiceAccount → Topic → ACL
          </p>
        </Card>

        <Card className="mt-6">
          <div className="flex items-center gap-2 mb-4">
            <div className="p-2 bg-red-100 dark:bg-red-900/30 rounded-lg">
              <XCircle className="text-red-600 dark:text-red-400" size={18} />
            </div>
            <h4 className="font-semibold">Failed Operation (Cross-Tenant Violation)</h4>
          </div>
          <CodeBlock code={failedLog} />
          <p className="text-sm text-gray-500 mt-4">
            Failed operations include the specific reason, making debugging straightforward.
          </p>
        </Card>
      </Section>

      {/* Kubernetes Events */}
      <Section title="Kubernetes Events" subtitle="Query events using kubectl">
        <Card>
          <div className="flex items-center gap-3 mb-6">
            <div className="p-2 bg-purple-100 dark:bg-purple-900/30 rounded-lg">
              <Search className="text-purple-600 dark:text-purple-400" size={20} />
            </div>
            <div>
              <h4 className="font-semibold">Native Kubernetes Event Integration</h4>
              <p className="text-sm text-gray-500">Use standard kubectl commands to query reconciliation events</p>
            </div>
          </div>
          <div className="space-y-4">
            {kubeEventExamples.map((example, index) => (
              <div key={index} className="flex items-start gap-4 p-4 bg-gray-50 dark:bg-gray-800 rounded-xl">
                <div className="p-2 bg-gray-200 dark:bg-gray-700 rounded-lg shrink-0">
                  <Filter size={16} className="text-gray-500" />
                </div>
                <div className="flex-1">
                  <code className="text-sm font-mono text-blue-600 dark:text-blue-400">{example.query}</code>
                  <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{example.description}</p>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </Section>

      {/* Key Benefits */}
      <Section title="Key Benefits">
        <CardGrid cols={2}>
          <Card>
            <div className="flex items-start gap-4">
              <CheckCircle2 className="text-green-500 shrink-0 mt-1" size={20} />
              <div>
                <h4 className="font-semibold">Complete Audit Trail</h4>
                <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                  Every CREATE, UPDATE, and DELETE is logged with full context. Know exactly what happened and when.
                </p>
              </div>
            </div>
          </Card>
          <Card>
            <div className="flex items-start gap-4">
              <CheckCircle2 className="text-green-500 shrink-0 mt-1" size={20} />
              <div>
                <h4 className="font-semibold">Fast Debugging</h4>
                <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                  Failed operations include detailed error messages. Find the root cause in seconds, not hours.
                </p>
              </div>
            </div>
          </Card>
          <Card>
            <div className="flex items-start gap-4">
              <CheckCircle2 className="text-green-500 shrink-0 mt-1" size={20} />
              <div>
                <h4 className="font-semibold">Compliance Ready</h4>
                <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                  Structured logs satisfy audit requirements. Export to your compliance system with confidence.
                </p>
              </div>
            </div>
          </Card>
          <Card>
            <div className="flex items-start gap-4">
              <CheckCircle2 className="text-green-500 shrink-0 mt-1" size={20} />
              <div>
                <h4 className="font-semibold">Operational Insights</h4>
                <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                  Understand usage patterns, track resource creation trends, and optimize your infrastructure.
                </p>
              </div>
            </div>
          </Card>
        </CardGrid>
      </Section>
    </PageLayout>
  )
}
