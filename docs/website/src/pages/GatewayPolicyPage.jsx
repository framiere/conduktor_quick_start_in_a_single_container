import { ArrowRight, Shield, Database, Users, Terminal, CheckCircle, AlertTriangle, Lock, Eye, Zap } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import Section from '../components/Section'
import CodeBlock from '../components/CodeBlock'

const policyCategories = [
  {
    name: 'Traffic Control',
    icon: Shield,
    color: 'blue',
    description: 'Enforce governance rules for topic creation, producer/consumer behavior, and rate limiting.',
    policies: [
      { name: 'CREATE_TOPIC_POLICY', description: 'Enforce partition count, replication factor, naming conventions' },
      { name: 'ALTER_TOPIC_POLICY', description: 'Control topic configuration changes' },
      { name: 'PRODUCE_POLICY', description: 'Enforce acks, compression settings' },
      { name: 'FETCH_POLICY', description: 'Control consumer fetch behavior' },
      { name: 'CONSUMER_GROUP_POLICY', description: 'Enforce consumer group settings' },
      { name: 'CLIENT_ID_POLICY', description: 'Require client ID naming conventions' },
      { name: 'PRODUCER_RATE_LIMITING', description: 'Add throughput quota limits' },
      { name: 'LIMIT_CONNECTION', description: 'Limit connection attempts' },
      { name: 'LIMIT_JOIN_GROUP', description: 'Limit consumer group joins per minute' }
    ]
  },
  {
    name: 'Data Security',
    icon: Lock,
    color: 'red',
    description: 'Protect sensitive data with encryption, masking, and audit logging.',
    policies: [
      { name: 'FIELD_ENCRYPTION', description: 'Field-level encryption on produce' },
      { name: 'FIELD_DECRYPTION', description: 'Field-level decryption on consume' },
      { name: 'DATA_MASKING', description: 'Mask sensitive fields (PII, etc.)' },
      { name: 'AUDIT', description: 'Audit Kafka API requests' },
      { name: 'HEADER_INJECTION', description: 'Inject dynamic headers' },
      { name: 'HEADER_REMOVAL', description: 'Remove headers matching patterns' }
    ]
  },
  {
    name: 'Data Quality',
    icon: CheckCircle,
    color: 'green',
    description: 'Validate message payloads and enforce schema compliance.',
    policies: [
      { name: 'SCHEMA_VALIDATION', description: 'Validate payload against schema' },
      { name: 'TOPIC_SCHEMA_ID_REQUIRED', description: 'Require schema ID for messages' }
    ]
  },
  {
    name: 'Advanced Patterns',
    icon: Zap,
    color: 'purple',
    description: 'Handle large messages and filter data using SQL/CEL expressions.',
    policies: [
      { name: 'LARGE_MESSAGE_HANDLING', description: 'Offload large messages to S3' },
      { name: 'SQL_TOPIC_FILTERING', description: 'Filter messages using SQL' },
      { name: 'CEL_TOPIC_FILTERING', description: 'Filter using CEL expressions' }
    ]
  },
  {
    name: 'Chaos Testing',
    icon: AlertTriangle,
    color: 'orange',
    description: 'Test system resilience by simulating failures and latency.',
    policies: [
      { name: 'CHAOS_LATENCY', description: 'Simulate network latency' },
      { name: 'CHAOS_SLOW_BROKER', description: 'Simulate slow broker responses' },
      { name: 'CHAOS_BROKEN_BROKER', description: 'Simulate broker failures' },
      { name: 'CHAOS_MESSAGE_CORRUPTION', description: 'Simulate message corruption' },
      { name: 'CHAOS_DUPLICATE_MESSAGES', description: 'Inject duplicate messages' }
    ]
  }
]

function PolicyCategoryCard({ category }) {
  const Icon = category.icon
  const colorClasses = {
    blue: 'bg-blue-500/20 text-blue-400 border-blue-500/30',
    red: 'bg-red-500/20 text-red-400 border-red-500/30',
    green: 'bg-green-500/20 text-green-400 border-green-500/30',
    purple: 'bg-purple-500/20 text-purple-400 border-purple-500/30',
    orange: 'bg-orange-500/20 text-orange-400 border-orange-500/30'
  }

  return (
    <div className="bg-gray-900 rounded-xl border border-gray-800 overflow-hidden">
      <div className={`px-6 py-4 border-b border-gray-800 ${colorClasses[category.color]}`}>
        <div className="flex items-center gap-3">
          <Icon size={24} />
          <h3 className="text-xl font-bold text-white">{category.name}</h3>
        </div>
        <p className="text-gray-300 mt-2 text-sm">{category.description}</p>
      </div>
      <div className="p-4">
        <div className="space-y-2">
          {category.policies.map((policy, idx) => (
            <div key={idx} className="flex items-start gap-3 text-sm">
              <code className="px-2 py-1 bg-gray-800 rounded text-blue-400 font-mono text-xs whitespace-nowrap">
                {policy.name}
              </code>
              <span className="text-gray-400">{policy.description}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

const scopeYaml = `apiVersion: messaging.example.com/v1
kind: Scope
metadata:
  name: payments-cluster-scope
  namespace: payments-team
spec:
  applicationServiceRef: payments-service
  clusterRef: payments-cluster`

const inputYaml = `apiVersion: messaging.example.com/v1
kind: GatewayPolicy
metadata:
  name: enforce-topic-partitions
  namespace: payments-team
spec:
  scopeRef: payments-cluster-scope
  policyType: CREATE_TOPIC_POLICY
  priority: 100
  config:
    topic: "payments-.*"
    numPartition:
      min: 3
      max: 12
      action: BLOCK
    replicationFactor:
      min: 3
      max: 3
      action: BLOCK`

const outputYaml = `apiVersion: gateway/v2
kind: Interceptor
metadata:
  name: payments-team--enforce-topic-partitions
  scope:
    vCluster: payments-prod-vcluster
spec:
  pluginClass: io.conduktor.gateway.interceptor.safeguard.CreateTopicPolicyPlugin
  priority: 100
  config:
    topic: "payments-.*"
    numPartition:
      min: 3
      max: 12
      action: BLOCK
    replicationFactor:
      min: 3
      max: 3
      action: BLOCK`

const dataMaskingExample = `apiVersion: messaging.example.com/v1
kind: GatewayPolicy
metadata:
  name: mask-pii-data
  namespace: payments-team
spec:
  scopeRef: payments-cluster-scope
  policyType: DATA_MASKING
  priority: 200
  config:
    topic: "payments.customers.*"
    fields:
      - fieldName: email
        rule: MASK_ALL
      - fieldName: phone
        rule: MASK_LAST_N
        nChars: 4
      - fieldName: ssn
        rule: MASK_ALL`

const transformerCode = `// GatewayPolicyTransformer.java
public ConduktorInterceptor transform(GatewayPolicy source) {
    GatewayPolicySpec spec = source.getSpec();
    String namespace = source.getMetadata().getNamespace();

    // Build namespaced name to avoid collisions
    String interceptorName = namespace + "--"
        + source.getMetadata().getName();

    // Resolve scopeRef → Scope → individual refs
    InterceptorScope scope = buildScope(spec, namespace);

    return ConduktorInterceptor.builder()
        .apiVersion("gateway/v2")
        .kind("Interceptor")
        .metadata(ConduktorInterceptorMetadata.builder()
                .name(interceptorName)
                .scope(scope)
                .build())
        .spec(ConduktorInterceptorSpec.builder()
                .pluginClass(spec.getPolicyType()
                    .getPluginClass())
                .priority(spec.getPriority())
                .config(spec.getConfig())
                .build())
        .build();
}`

export default function GatewayPolicyPage() {
  return (
    <PageLayout
      title="Gateway Policies"
      subtitle="Declare governance, security, and data quality policies as Kubernetes CRDs"
      icon={Shield}
      aphorism={{
        text: "The best way to predict the future is to implement it.",
        author: "David Heinemeier Hansson"
      }}
    >
      <Section title="Overview" icon={Shield}>
        <p className="text-gray-300 mb-6">
          GatewayPolicy CRDs allow teams to declare Conduktor Gateway interceptors using Kubernetes-native resources.
          The operator transforms these policies into Conduktor Gateway Interceptor format, enabling governance,
          security, and data quality enforcement without modifying client applications.
        </p>
        <div className="bg-gray-900 rounded-xl border border-gray-800 p-6">
          <div className="flex flex-col lg:flex-row items-center justify-center gap-4 lg:gap-8">
            <div className="flex flex-col items-center gap-2">
              <div className="text-sm text-gray-400 mb-2">Internal CRD</div>
              <div className="px-4 py-2 bg-blue-500/20 text-blue-400 rounded-lg border border-blue-500/30 text-sm">
                GatewayPolicy
              </div>
            </div>
            <ArrowRight size={24} className="text-gray-600 rotate-90 lg:rotate-0" />
            <div className="px-6 py-4 bg-purple-500/20 text-purple-400 rounded-xl border border-purple-500/30 text-center">
              <Database size={24} className="mx-auto mb-2" />
              <div className="font-semibold">Transformer</div>
              <div className="text-xs text-gray-400 mt-1">PolicyType → pluginClass</div>
            </div>
            <ArrowRight size={24} className="text-gray-600 rotate-90 lg:rotate-0" />
            <div className="flex flex-col items-center gap-2">
              <div className="text-sm text-gray-400 mb-2">Conduktor Gateway</div>
              <div className="px-4 py-2 bg-green-500/20 text-green-400 rounded-lg border border-green-500/30 text-sm">
                Interceptor
              </div>
            </div>
          </div>
        </div>
      </Section>

      <Section title="Policy Categories" icon={Database}>
        <p className="text-gray-300 mb-6">
          27 policy types organized into 5 categories, covering all major Conduktor Gateway interceptor capabilities.
        </p>
        <div className="grid lg:grid-cols-2 gap-6">
          {policyCategories.map((category, idx) => (
            <PolicyCategoryCard key={idx} category={category} />
          ))}
        </div>
      </Section>

      <Section title="Transformation Example" icon={ArrowRight}>
        <div className="bg-gray-900 rounded-xl border border-gray-800 overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-800 bg-blue-500/10">
            <h3 className="text-lg font-bold text-white">CreateTopicPolicy Transformation</h3>
            <p className="text-gray-400 text-sm mt-1">
              Enforces partition count and replication factor rules on topic creation
            </p>
          </div>
          <div className="grid lg:grid-cols-2 gap-0 lg:divide-x divide-gray-800">
            <div className="p-4">
              <div className="flex items-center gap-2 text-sm text-gray-400 mb-3">
                <span className="px-2 py-1 bg-cyan-500/20 text-cyan-400 rounded">SCOPE</span>
                <span>Scope CRD (targeting)</span>
              </div>
              <CodeBlock code={scopeYaml} language="yaml" />
              <div className="flex items-center gap-2 text-sm text-gray-400 mb-3 mt-4">
                <span className="px-2 py-1 bg-blue-500/20 text-blue-400 rounded">INPUT</span>
                <span>GatewayPolicy CRD</span>
              </div>
              <CodeBlock code={inputYaml} language="yaml" />
            </div>
            <div className="p-4 border-t lg:border-t-0 border-gray-800">
              <div className="flex items-center gap-2 text-sm text-gray-400 mb-3">
                <span className="px-2 py-1 bg-green-500/20 text-green-400 rounded">OUTPUT</span>
                <span>Conduktor Interceptor</span>
              </div>
              <CodeBlock code={outputYaml} language="yaml" />
            </div>
          </div>
          <div className="px-6 py-4 bg-gray-950 border-t border-gray-800">
            <h4 className="text-sm font-semibold text-gray-400 mb-3">Key Mappings</h4>
            <div className="space-y-2">
              <div className="flex items-center gap-3 text-sm">
                <code className="px-2 py-1 bg-gray-800 rounded text-blue-400 font-mono text-xs">namespace + name</code>
                <ArrowRight size={14} className="text-gray-600" />
                <code className="px-2 py-1 bg-gray-800 rounded text-green-400 font-mono text-xs">metadata.name</code>
                <span className="text-gray-500 text-xs">Namespaced to avoid collisions</span>
              </div>
              <div className="flex items-center gap-3 text-sm">
                <code className="px-2 py-1 bg-gray-800 rounded text-blue-400 font-mono text-xs">scopeRef</code>
                <ArrowRight size={14} className="text-gray-600" />
                <code className="px-2 py-1 bg-gray-800 rounded text-green-400 font-mono text-xs">scope.vCluster</code>
                <span className="text-gray-500 text-xs">Resolved via Scope → KafkaCluster lookup</span>
              </div>
              <div className="flex items-center gap-3 text-sm">
                <code className="px-2 py-1 bg-gray-800 rounded text-blue-400 font-mono text-xs">policyType</code>
                <ArrowRight size={14} className="text-gray-600" />
                <code className="px-2 py-1 bg-gray-800 rounded text-green-400 font-mono text-xs">spec.pluginClass</code>
                <span className="text-gray-500 text-xs">Enum maps to Conduktor plugin class</span>
              </div>
            </div>
          </div>
        </div>
      </Section>

      <Section title="Data Security Example" icon={Lock}>
        <CardGrid>
          <Card title="PII Data Masking" icon={Eye} color="red">
            <p className="text-gray-400 text-sm mb-4">
              Mask sensitive fields like email, phone, and SSN in consumed messages.
              Multiple masking rules available: MASK_ALL, MASK_LAST_N, and more.
            </p>
            <CodeBlock code={dataMaskingExample} language="yaml" />
          </Card>
          <Card title="Implementation" icon={Terminal} color="purple">
            <p className="text-gray-400 text-sm mb-4">
              The transformer resolves references and maps PolicyType enum to
              the correct Conduktor Gateway plugin class.
            </p>
            <CodeBlock code={transformerCode} language="java" />
          </Card>
        </CardGrid>
      </Section>

      <Section title="Scoping via Scope CRD" icon={Users}>
        <p className="text-gray-300 mb-6">
          Policies reference a <code className="text-cyan-400">Scope</code> CRD that bundles targeting fields.
          This makes scoping reusable — multiple policies can share the same Scope.
        </p>
        <div className="grid lg:grid-cols-2 gap-6 mb-6">
          <div>
            <div className="flex items-center gap-2 text-sm text-gray-400 mb-3">
              <span className="px-2 py-1 bg-cyan-500/20 text-cyan-400 rounded">Scope CRD</span>
              <span>Defines where policies apply</span>
            </div>
            <CodeBlock code={scopeYaml} language="yaml" />
          </div>
          <div>
            <div className="flex items-center gap-2 text-sm text-gray-400 mb-3">
              <span className="px-2 py-1 bg-blue-500/20 text-blue-400 rounded">GatewayPolicy</span>
              <span>References Scope via scopeRef</span>
            </div>
            <CodeBlock code={inputYaml} language="yaml" />
          </div>
        </div>
        <CardGrid cols={3}>
          <Card title="vCluster Scope" icon={Database} color="blue">
            <p className="text-gray-400 text-sm">
              Apply policy to all users within a specific virtual cluster.
              Set via <code className="text-blue-400">clusterRef</code> on the Scope.
            </p>
          </Card>
          <Card title="User Scope" icon={Users} color="green">
            <p className="text-gray-400 text-sm">
              Apply policy to a specific service account.
              Set via <code className="text-green-400">serviceAccountRef</code> on the Scope.
            </p>
          </Card>
          <Card title="Group Scope" icon={Shield} color="purple">
            <p className="text-gray-400 text-sm">
              Apply policy to a group of users.
              Set via <code className="text-purple-400">groupRef</code> on the Scope.
            </p>
          </Card>
        </CardGrid>
      </Section>

      <Section title="Ownership Chain" icon={CheckCircle}>
        <div className="bg-gray-900 rounded-xl border border-gray-800 p-6">
          <pre className="text-sm text-gray-300 font-mono">
{`ApplicationService (root)
├── KafkaCluster → VirtualCluster
├── ServiceAccount → GatewayServiceAccount
│   ├── Topic → ConduktorTopic
│   ├── ACL
│   └── ConsumerGroup
├── Scope (bundles cluster/serviceAccount/group targeting)
└── GatewayPolicy → Interceptor (via scopeRef)`}
          </pre>
        </div>
        <p className="text-gray-400 mt-4">
          Scope follows the standard ownership model via <code className="text-blue-400">applicationServiceRef</code>.
          GatewayPolicy references a Scope via <code className="text-cyan-400">scopeRef</code>, decoupling
          policy logic from targeting configuration.
        </p>
      </Section>
    </PageLayout>
  )
}
