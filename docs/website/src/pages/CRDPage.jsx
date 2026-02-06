import { Boxes, Layers, Database, Users, Lock, MessageSquare, Shield } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import Section from '../components/Section'
import CodeBlock from '../components/CodeBlock'
import DiagramBox, { DiagramNode } from '../components/DiagramBox'
import slugify from '../utils/slugify'

const crds = [
  {
    name: 'ApplicationService',
    icon: Layers,
    color: 'blue',
    description: 'Root resource representing a tenant or application. All other resources must reference an ApplicationService.',
    fields: [
      { name: 'name', type: 'string', required: true, description: 'Display name of the application service' }
    ],
    yaml: `apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: team-a-app
spec:
  name: team-a-app`
  },
  {
    name: 'KafkaCluster',
    icon: Database,
    color: 'purple',
    description: 'Represents a logical Kafka cluster mapped to an ApplicationService. Provides cluster isolation and authentication type configuration.',
    fields: [
      { name: 'clusterId', type: 'string', required: true, description: 'Unique identifier for the virtual cluster' },
      { name: 'applicationServiceRef', type: 'string', required: true, description: 'Reference to owning ApplicationService' },
      { name: 'authType', type: 'AuthType', required: false, description: 'Authentication type: MTLS or SASL_SSL (defaults to MTLS)' }
    ],
    yaml: `apiVersion: messaging.example.com/v1
kind: KafkaCluster
metadata:
  name: team-a-cluster
spec:
  clusterId: team-a-cluster
  applicationServiceRef: team-a-app
  authType: MTLS`
  },
  {
    name: 'ServiceAccount',
    icon: Users,
    color: 'green',
    description: 'Identity for authentication. Maps certificate CN (MTLS) or username (SASL_SSL) to permissions.',
    fields: [
      { name: 'name', type: 'string', required: true, description: 'Service account identifier' },
      { name: 'dn', type: 'string[]', required: false, description: 'Distinguished names for certificate mapping (MTLS only, optional)' },
      { name: 'clusterRef', type: 'string', required: true, description: 'Reference to KafkaCluster' },
      { name: 'applicationServiceRef', type: 'string', required: true, description: 'Reference to owning ApplicationService' }
    ],
    yaml: `apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: team-a-sa
spec:
  name: team-a-sa
  dn:
    - "CN=team-a-sa"
  clusterRef: team-a-cluster
  applicationServiceRef: team-a-app`
  },
  {
    name: 'Topic',
    icon: MessageSquare,
    color: 'orange',
    description: 'Kafka topic configuration with partitions and replication settings.',
    fields: [
      { name: 'name', type: 'string', required: true, description: 'Topic name' },
      { name: 'serviceRef', type: 'string', required: true, description: 'Reference to ServiceAccount' },
      { name: 'applicationServiceRef', type: 'string', required: true, description: 'Reference to owning ApplicationService' },
      { name: 'partitions', type: 'integer', required: false, description: 'Number of partitions (default: 6)' },
      { name: 'replicationFactor', type: 'integer', required: false, description: 'Replication factor (default: 3)' },
      { name: 'config', type: 'Map<String,String>', required: false, description: 'Topic configuration key-value pairs' }
    ],
    yaml: `apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: orders-topic
spec:
  name: orders
  serviceRef: team-a-sa
  applicationServiceRef: team-a-app
  partitions: 3
  replicationFactor: 1
  config:
    retention.ms: "604800000"
    cleanup.policy: delete`
  },
  {
    name: 'ACL',
    icon: Lock,
    color: 'red',
    description: 'Access Control List defining permissions for ServiceAccounts on Topics or ConsumerGroups.',
    fields: [
      { name: 'serviceRef', type: 'string', required: true, description: 'Reference to ServiceAccount' },
      { name: 'applicationServiceRef', type: 'string', required: true, description: 'Reference to owning ApplicationService' },
      { name: 'topicRef', type: 'string', required: false, description: 'Reference to Topic (mutually exclusive with consumerGroupRef)' },
      { name: 'consumerGroupRef', type: 'string', required: false, description: 'Reference to ConsumerGroup (mutually exclusive with topicRef)' },
      { name: 'operations', type: 'Operation[]', required: true, description: 'Operations: READ, WRITE, DESCRIBE, ALTER, etc.' },
      { name: 'host', type: 'string', required: false, description: 'Host from which operations are allowed (default: "*")' },
      { name: 'permission', type: 'Permission', required: false, description: 'ALLOW or DENY (default: ALLOW)' }
    ],
    yaml: `apiVersion: messaging.example.com/v1
kind: ACL
metadata:
  name: orders-acl
spec:
  serviceRef: team-a-sa
  applicationServiceRef: team-a-app
  topicRef: orders-topic
  operations:
    - READ
    - WRITE
  host: "*"
  permission: ALLOW`
  },
  {
    name: 'ConsumerGroup',
    icon: Users,
    color: 'gray',
    description: 'Consumer group configuration for coordinated message consumption.',
    fields: [
      { name: 'name', type: 'string', required: true, description: 'Consumer group name or prefix' },
      { name: 'serviceRef', type: 'string', required: true, description: 'Reference to ServiceAccount' },
      { name: 'applicationServiceRef', type: 'string', required: true, description: 'Reference to owning ApplicationService' },
      { name: 'patternType', type: 'PatternType', required: false, description: 'LITERAL or PREFIXED matching (default: LITERAL)' }
    ],
    yaml: `apiVersion: messaging.example.com/v1
kind: ConsumerGroup
metadata:
  name: orders-cg
spec:
  name: orders-cg
  serviceRef: team-a-sa
  applicationServiceRef: team-a-app
  patternType: LITERAL`
  },
  {
    name: 'Scope',
    icon: Database,
    color: 'cyan',
    description: 'Defines where a GatewayPolicy applies — bundles cluster, service account, and group targeting into a reusable resource.',
    fields: [
      { name: 'applicationServiceRef', type: 'string', required: true, description: 'Reference to owning ApplicationService' },
      { name: 'clusterRef', type: 'string', required: true, description: 'Reference to KafkaCluster (resolves to vCluster)' },
      { name: 'serviceAccountRef', type: 'string', required: false, description: 'Reference to ServiceAccount (resolves to username)' },
      { name: 'groupRef', type: 'string', required: false, description: 'Target a specific group of users' }
    ],
    yaml: `apiVersion: messaging.example.com/v1
kind: Scope
metadata:
  name: team-a-cluster-scope
spec:
  applicationServiceRef: team-a-app
  clusterRef: team-a-cluster`
  },
  {
    name: 'GatewayPolicy',
    icon: Shield,
    color: 'purple',
    description: 'Configures Conduktor Gateway interceptors for governance, security, and data quality policies. References a Scope for targeting.',
    fields: [
      { name: 'scopeRef', type: 'string', required: true, description: 'Reference to a Scope defining where this policy applies' },
      { name: 'policyType', type: 'PolicyType', required: true, description: 'Type of policy (CreateTopicPolicy, SchemaValidation, FieldEncryption, etc.)' },
      { name: 'priority', type: 'integer', required: true, description: 'Policy execution priority (lower = earlier)' },
      { name: 'config', type: 'Map<String,Object>', required: false, description: 'Policy-specific configuration' }
    ],
    yaml: `apiVersion: messaging.example.com/v1
kind: GatewayPolicy
metadata:
  name: topic-creation-policy
spec:
  scopeRef: team-a-cluster-scope
  policyType: CREATE_TOPIC_POLICY
  priority: 1
  config:
    replicationFactor:
      min: 3
    numPartition:
      min: 3
      max: 100`
  }
]

export default function CRDPage() {
  return (
    <PageLayout
      title="Custom Resource Definitions"
      subtitle="Deep dive into the 8 CRD types and their relationships"
      icon={Boxes}
      breadcrumb="CRDs"
      aphorism={{
        text: "The limits of my language mean the limits of my world.",
        author: "Ludwig Wittgenstein"
      }}
    >
      {/* Overview */}
      <Section title="CRD Overview" subtitle="All resources belong to the messaging.example.com/v1 API group">
        <DiagramBox title="Resource Relationships">
          <div className="py-8">
            <div className="flex flex-col items-center space-y-4">
              {/* Level 0 */}
              <DiagramNode color="blue" className="px-8">
                <span className="font-bold">ApplicationService</span>
                <span className="text-xs block opacity-70">Root tenant boundary</span>
              </DiagramNode>

              <div className="text-2xl text-gray-400">↓</div>

              {/* Level 1 */}
              <div className="flex gap-8">
                <DiagramNode color="purple">
                  <span className="font-bold">KafkaCluster</span>
                  <span className="text-xs block opacity-70">Logical Kafka cluster</span>
                </DiagramNode>
              </div>

              <div className="text-2xl text-gray-400">↓</div>

              {/* Level 2 */}
              <DiagramNode color="green">
                <span className="font-bold">ServiceAccount</span>
                <span className="text-xs block opacity-70">Client identity (MTLS / SASL_SSL)</span>
              </DiagramNode>

              <div className="text-2xl text-gray-400">↓</div>

              {/* Level 3 */}
              <div className="flex gap-6">
                <DiagramNode color="orange">
                  <span className="font-bold">Topic</span>
                </DiagramNode>
                <DiagramNode color="red">
                  <span className="font-bold">ACL</span>
                </DiagramNode>
                <DiagramNode color="gray">
                  <span className="font-bold">ConsumerGroup</span>
                </DiagramNode>
                <DiagramNode color="cyan">
                  <span className="font-bold">Scope</span>
                  <span className="text-xs block opacity-70">Policy targeting</span>
                </DiagramNode>
              </div>

              <div className="text-2xl text-gray-400">↓</div>

              {/* Level 4 - GatewayPolicy references Scope */}
              <DiagramNode color="purple">
                <span className="font-bold">GatewayPolicy</span>
                <span className="text-xs block opacity-70">→ Interceptor</span>
              </DiagramNode>
            </div>
          </div>
        </DiagramBox>
      </Section>

      {/* Each CRD */}
      {crds.map((crd, index) => {
        const Icon = crd.icon
        return (
          <Section key={index} id={slugify(crd.name)} title="">
            <div className="border border-gray-200 dark:border-gray-800 rounded-2xl overflow-hidden">
              {/* Header */}
              <div className={`
                p-6 flex items-center gap-4
                ${crd.color === 'blue' ? 'bg-blue-50 dark:bg-blue-900/20' : ''}
                ${crd.color === 'purple' ? 'bg-purple-50 dark:bg-purple-900/20' : ''}
                ${crd.color === 'green' ? 'bg-green-50 dark:bg-green-900/20' : ''}
                ${crd.color === 'orange' ? 'bg-orange-50 dark:bg-orange-900/20' : ''}
                ${crd.color === 'red' ? 'bg-red-50 dark:bg-red-900/20' : ''}
                ${crd.color === 'gray' ? 'bg-gray-50 dark:bg-gray-800' : ''}
                ${crd.color === 'cyan' ? 'bg-cyan-50 dark:bg-cyan-900/20' : ''}
              `}>
                <div className={`
                  p-3 rounded-xl
                  ${crd.color === 'blue' ? 'bg-blue-100 dark:bg-blue-900/50 text-blue-600 dark:text-blue-400' : ''}
                  ${crd.color === 'purple' ? 'bg-purple-100 dark:bg-purple-900/50 text-purple-600 dark:text-purple-400' : ''}
                  ${crd.color === 'green' ? 'bg-green-100 dark:bg-green-900/50 text-green-600 dark:text-green-400' : ''}
                  ${crd.color === 'orange' ? 'bg-orange-100 dark:bg-orange-900/50 text-orange-600 dark:text-orange-400' : ''}
                  ${crd.color === 'red' ? 'bg-red-100 dark:bg-red-900/50 text-red-600 dark:text-red-400' : ''}
                  ${crd.color === 'gray' ? 'bg-gray-200 dark:bg-gray-700 text-gray-600 dark:text-gray-400' : ''}
                  ${crd.color === 'cyan' ? 'bg-cyan-100 dark:bg-cyan-900/50 text-cyan-600 dark:text-cyan-400' : ''}
                `}>
                  <Icon size={28} />
                </div>
                <div>
                  <h2 className="text-2xl font-bold">{crd.name}</h2>
                  <p className="text-gray-600 dark:text-gray-400 mt-1">{crd.description}</p>
                </div>
              </div>

              {/* Content */}
              <div className="p-6 bg-white dark:bg-gray-900">
                <div className="grid lg:grid-cols-2 gap-8">
                  {/* Fields */}
                  <div>
                    <h4 className="font-semibold mb-4">Spec Fields</h4>
                    <div className="space-y-3">
                      {crd.fields.map((field, i) => (
                        <div key={i} className="flex items-start gap-3 p-3 bg-gray-50 dark:bg-gray-800 rounded-lg">
                          <code className="px-2 py-0.5 bg-gray-200 dark:bg-gray-700 rounded text-sm font-mono">
                            {field.name}
                          </code>
                          <div className="flex-1">
                            <div className="flex items-center gap-2">
                              <span className="text-xs text-gray-500">{field.type}</span>
                              {field.required && (
                                <span className="px-1.5 py-0.5 bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 rounded text-xs">
                                  required
                                </span>
                              )}
                            </div>
                            <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{field.description}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* YAML Example */}
                  <div>
                    <h4 className="font-semibold mb-4">Example YAML</h4>
                    <CodeBlock
                      language="yaml"
                      code={crd.yaml}
                    />
                  </div>
                </div>
              </div>
            </div>
          </Section>
        )
      })}

      {/* API Group Info */}
      <Section title="API Group Information">
        <Card>
          <div className="grid md:grid-cols-2 gap-6">
            <div>
              <h4 className="font-semibold mb-3">Group & Version</h4>
              <div className="space-y-2 text-sm">
                <div><span className="text-gray-500">API Group:</span> <code>messaging.example.com</code></div>
                <div><span className="text-gray-500">Version:</span> <code>v1</code></div>
                <div><span className="text-gray-500">Full Prefix:</span> <code>messaging.example.com/v1</code></div>
              </div>
            </div>
            <div>
              <h4 className="font-semibold mb-3">Kubectl Commands</h4>
              <CodeBlock
                code={`# List all CRDs
kubectl get crds | grep messaging

# Get all Topics
kubectl get topics -A

# Get specific ApplicationService
kubectl get applicationservice my-app -o yaml`}
              />
            </div>
          </div>
        </Card>
      </Section>
    </PageLayout>
  )
}
