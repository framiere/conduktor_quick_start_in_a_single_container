import { Network, ArrowRight, CheckCircle2, XCircle, Clock, Database, Server, Shield, MessageSquare } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import DiagramBox, { DiagramNode, DiagramArrow } from '../components/DiagramBox'
import Section from '../components/Section'

const createTopicSteps = [
  { step: 1, actor: 'User', action: 'kubectl apply -f topic.yaml', icon: MessageSquare, color: 'gray' },
  { step: 2, actor: 'kubectl', action: 'Sends HTTP POST to API Server', icon: ArrowRight, color: 'gray' },
  { step: 3, actor: 'API Server', action: 'Receives CREATE Topic request', icon: Server, color: 'blue' },
  { step: 4, actor: 'API Server', action: 'Calls ValidatingWebhookConfiguration', icon: Shield, color: 'blue' },
  { step: 5, actor: 'Webhook', action: 'Receives AdmissionReview request', icon: Shield, color: 'purple' },
  { step: 6, actor: 'Webhook', action: 'Parses Topic from request body', icon: Database, color: 'purple' },
  { step: 7, actor: 'Webhook', action: 'Validates ownership chain', icon: CheckCircle2, color: 'purple' },
  { step: 8, actor: 'Webhook', action: 'Returns AdmissionReview response', icon: ArrowRight, color: 'purple' },
  { step: 9, actor: 'API Server', action: 'Persists Topic to etcd (if allowed)', icon: Database, color: 'blue' },
  { step: 10, actor: 'API Server', action: 'Returns result to kubectl', icon: ArrowRight, color: 'blue' },
]

const eventTypes = [
  { type: 'BEFORE_CREATE', description: 'Published before resource is created in store', timing: 'Synchronous' },
  { type: 'AFTER_CREATE', description: 'Published after resource is successfully created', timing: 'Synchronous' },
  { type: 'BEFORE_UPDATE', description: 'Published before resource is updated', timing: 'Synchronous' },
  { type: 'AFTER_UPDATE', description: 'Published after resource is successfully updated', timing: 'Synchronous' },
  { type: 'BEFORE_DELETE', description: 'Published before resource is deleted', timing: 'Synchronous' },
  { type: 'AFTER_DELETE', description: 'Published after resource is successfully deleted', timing: 'Synchronous' },
]

export default function DataFlowPage() {
  return (
    <PageLayout
      title="Data Flow"
      subtitle="Visualizing request lifecycle and event propagation"
      icon={Network}
      breadcrumb="Data Flow"
    >
      {/* Topic Creation Flow */}
      <Section title="Topic Creation Flow" subtitle="Step-by-step request lifecycle">
        <div className="space-y-4">
          {createTopicSteps.map((item) => {
            const Icon = item.icon
            return (
              <div
                key={item.step}
                className={`
                  flex items-center gap-4 p-4 rounded-xl border-2 transition-all
                  ${item.color === 'gray' ? 'bg-gray-50 dark:bg-gray-900 border-gray-200 dark:border-gray-800' : ''}
                  ${item.color === 'blue' ? 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800' : ''}
                  ${item.color === 'purple' ? 'bg-purple-50 dark:bg-purple-900/20 border-purple-200 dark:border-purple-800' : ''}
                `}
              >
                <div className={`
                  w-10 h-10 rounded-full flex items-center justify-center font-bold text-white shrink-0
                  ${item.color === 'gray' ? 'bg-gray-500' : ''}
                  ${item.color === 'blue' ? 'bg-blue-500' : ''}
                  ${item.color === 'purple' ? 'bg-purple-500' : ''}
                `}>
                  {item.step}
                </div>
                <div className={`
                  p-2 rounded-lg shrink-0
                  ${item.color === 'gray' ? 'bg-gray-200 dark:bg-gray-700 text-gray-600 dark:text-gray-400' : ''}
                  ${item.color === 'blue' ? 'bg-blue-200 dark:bg-blue-800 text-blue-600 dark:text-blue-400' : ''}
                  ${item.color === 'purple' ? 'bg-purple-200 dark:bg-purple-800 text-purple-600 dark:text-purple-400' : ''}
                `}>
                  <Icon size={20} />
                </div>
                <div className="flex-1">
                  <div className="font-semibold">{item.actor}</div>
                  <div className="text-sm text-gray-600 dark:text-gray-400">{item.action}</div>
                </div>
              </div>
            )
          })}
        </div>
      </Section>

      {/* Validation Decision Tree */}
      <Section title="Validation Decision Tree" subtitle="How the webhook makes accept/reject decisions">
        <DiagramBox title="Validation Logic">
          <div className="py-8 flex flex-col items-center space-y-4">
            <DiagramNode color="purple" className="w-64">
              Receive AdmissionReview
            </DiagramNode>

            <DiagramArrow direction="down" />

            <DiagramNode color="blue" className="w-64">
              Parse Resource from request
            </DiagramNode>

            <DiagramArrow direction="down" />

            <div className="border-2 border-dashed border-orange-300 dark:border-orange-700 rounded-xl p-6 bg-orange-50/50 dark:bg-orange-900/10">
              <div className="text-sm font-semibold text-orange-600 dark:text-orange-400 mb-4 text-center">
                Validation Checks
              </div>

              <div className="space-y-3">
                <div className="flex items-center gap-3 px-4 py-2 bg-white dark:bg-gray-900 rounded-lg">
                  <span className="text-sm">Has applicationServiceRef?</span>
                </div>
                <div className="flex items-center gap-3 px-4 py-2 bg-white dark:bg-gray-900 rounded-lg">
                  <span className="text-sm">ApplicationService exists?</span>
                </div>
                <div className="flex items-center gap-3 px-4 py-2 bg-white dark:bg-gray-900 rounded-lg">
                  <span className="text-sm">Referenced resources exist?</span>
                </div>
                <div className="flex items-center gap-3 px-4 py-2 bg-white dark:bg-gray-900 rounded-lg">
                  <span className="text-sm">Same owner for all refs?</span>
                </div>
                <div className="flex items-center gap-3 px-4 py-2 bg-white dark:bg-gray-900 rounded-lg">
                  <span className="text-sm">Owner not changed (on UPDATE)?</span>
                </div>
              </div>
            </div>

            <div className="flex gap-16">
              <div className="flex flex-col items-center">
                <span className="text-sm text-gray-500 mb-2">All pass</span>
                <DiagramArrow direction="down" />
                <DiagramNode color="green" className="w-40">
                  <div className="flex items-center justify-center gap-2">
                    <CheckCircle2 size={18} />
                    <span>ALLOW</span>
                  </div>
                </DiagramNode>
              </div>
              <div className="flex flex-col items-center">
                <span className="text-sm text-gray-500 mb-2">Any fail</span>
                <DiagramArrow direction="down" />
                <DiagramNode color="red" className="w-40">
                  <div className="flex items-center justify-center gap-2">
                    <XCircle size={18} />
                    <span>DENY</span>
                  </div>
                </DiagramNode>
              </div>
            </div>
          </div>
        </DiagramBox>
      </Section>

      {/* Event Flow */}
      <Section title="Event Flow" subtitle="Reconciliation events published by CRDStore">
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 dark:bg-gray-800">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold">Event Type</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">Description</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">Timing</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
              {eventTypes.map((event) => (
                <tr key={event.type}>
                  <td className="px-6 py-4">
                    <code className="text-sm bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded">
                      {event.type}
                    </code>
                  </td>
                  <td className="px-6 py-4 text-gray-600 dark:text-gray-400">{event.description}</td>
                  <td className="px-6 py-4">
                    <span className="px-2 py-1 bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded text-xs">
                      {event.timing}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-6">
          <DiagramBox title="Event Sequence">
            <div className="py-6 flex items-center justify-center gap-4">
              <DiagramNode color="orange" className="w-48">
                store.create(topic)
              </DiagramNode>
              <DiagramArrow direction="right" />
              <DiagramNode color="blue" className="w-48">
                BEFORE_CREATE
              </DiagramNode>
              <DiagramArrow direction="right" />
              <DiagramNode color="purple" className="w-48">
                Persist to map
              </DiagramNode>
              <DiagramArrow direction="right" />
              <DiagramNode color="green" className="w-48">
                AFTER_CREATE
              </DiagramNode>
            </div>
          </DiagramBox>
        </div>
      </Section>

      {/* mTLS Data Flow */}
      <Section title="mTLS Authentication Flow" subtitle="Certificate-based identity in action">
        <DiagramBox title="Client to Topic Access">
          <div className="py-8">
            <div className="flex flex-col lg:flex-row items-center justify-center gap-8">
              {/* Client */}
              <div className="flex flex-col items-center gap-2">
                <DiagramNode color="gray" className="w-48">
                  Kafka Client
                </DiagramNode>
                <span className="text-xs text-gray-500">CN=team-a-sa</span>
              </div>

              <div className="flex flex-col items-center">
                <DiagramArrow direction="right" label="TLS handshake" />
                <span className="text-xs text-gray-500 mt-1">client cert</span>
              </div>

              {/* Gateway */}
              <div className="flex flex-col items-center gap-2">
                <DiagramNode color="purple" className="w-48">
                  Conduktor Gateway
                </DiagramNode>
                <span className="text-xs text-gray-500">Extracts CN</span>
              </div>

              <div className="flex flex-col items-center">
                <DiagramArrow direction="right" label="lookup" />
              </div>

              {/* ServiceAccount */}
              <div className="flex flex-col items-center gap-2">
                <DiagramNode color="green" className="w-48">
                  ServiceAccount CRD
                </DiagramNode>
                <span className="text-xs text-gray-500">dn: CN=team-a-sa</span>
              </div>

              <div className="flex flex-col items-center">
                <DiagramArrow direction="right" label="check ACL" />
              </div>

              {/* Topic */}
              <div className="flex flex-col items-center gap-2">
                <DiagramNode color="orange" className="w-48">
                  Topic Access
                </DiagramNode>
                <span className="text-xs text-green-500">READ/WRITE</span>
              </div>
            </div>
          </div>
        </DiagramBox>
      </Section>

      {/* Summary */}
      <Section title="Key Takeaways">
        <CardGrid cols={3}>
          <Card>
            <div className="flex items-center gap-3 mb-3">
              <div className="p-2 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
                <Clock className="text-blue-600 dark:text-blue-400" size={20} />
              </div>
              <h4 className="font-semibold">Synchronous Validation</h4>
            </div>
            <p className="text-sm text-gray-600 dark:text-gray-400">
              All validation happens synchronously before resources are persisted.
              Invalid resources never reach etcd.
            </p>
          </Card>
          <Card>
            <div className="flex items-center gap-3 mb-3">
              <div className="p-2 bg-green-100 dark:bg-green-900/30 rounded-lg">
                <Database className="text-green-600 dark:text-green-400" size={20} />
              </div>
              <h4 className="font-semibold">Event-Driven</h4>
            </div>
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Every store operation publishes BEFORE/AFTER events for
              audit logging and external integration.
            </p>
          </Card>
          <Card>
            <div className="flex items-center gap-3 mb-3">
              <div className="p-2 bg-purple-100 dark:bg-purple-900/30 rounded-lg">
                <Shield className="text-purple-600 dark:text-purple-400" size={20} />
              </div>
              <h4 className="font-semibold">Zero Trust</h4>
            </div>
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Every request is validated. mTLS ensures identity.
              Ownership chains ensure isolation.
            </p>
          </Card>
        </CardGrid>
      </Section>
    </PageLayout>
  )
}
