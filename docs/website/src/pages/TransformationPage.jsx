import { ArrowRight, RefreshCw, Database, Users, MessageSquare, Terminal, CheckCircle } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import Section from '../components/Section'
import CodeBlock from '../components/CodeBlock'

const transformations = [
  {
    name: 'KafkaCluster → VirtualCluster',
    icon: Database,
    color: 'purple',
    description: 'Transforms internal KafkaCluster CRD to Conduktor Gateway VirtualCluster format.',
    inputYaml: `apiVersion: messaging.example.com/v1
kind: KafkaCluster
metadata:
  name: production-cluster
  namespace: payments-team
spec:
  clusterId: payments-prod-vcluster
  applicationServiceRef: payments-service`,
    outputYaml: `apiVersion: gateway/v2
kind: VirtualCluster
metadata:
  name: payments-prod-vcluster
spec:
  aclEnabled: true`,
    mappings: [
      { from: 'spec.clusterId', to: 'metadata.name', note: 'Cluster ID becomes the VirtualCluster name' },
      { from: '-', to: 'spec.aclEnabled', note: 'Always set to true by default' }
    ]
  },
  {
    name: 'ServiceAccount → GatewayServiceAccount',
    icon: Users,
    color: 'green',
    description: 'Transforms ServiceAccount with DN extraction from certificate distinguished names.',
    inputYaml: `apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: payments-admin-sa
  namespace: payments-team
spec:
  name: payments-admin
  clusterRef: payments-prod-vcluster
  applicationServiceRef: payments-service
  dn:
    - "CN=payments-admin,OU=ServiceAccounts,O=Company"
    - "CN=payments-backup,OU=ServiceAccounts,O=Company"`,
    outputYaml: `apiVersion: gateway/v2
kind: GatewayServiceAccount
metadata:
  name: payments-admin
  vCluster: payments-prod-vcluster
spec:
  type: EXTERNAL
  externalNames:
    - payments-admin
    - payments-backup`,
    mappings: [
      { from: 'spec.name', to: 'metadata.name', note: 'ServiceAccount name' },
      { from: 'spec.clusterRef', to: 'metadata.vCluster', note: 'Links to VirtualCluster' },
      { from: 'spec.dn[].CN=*', to: 'spec.externalNames[]', note: 'Extracts CN from each DN' },
      { from: '-', to: 'spec.type', note: 'Always EXTERNAL for mTLS mapping' }
    ]
  },
  {
    name: 'Topic → ConduktorTopic',
    icon: MessageSquare,
    color: 'orange',
    description: 'Transforms Topic with cluster resolution via ServiceAccount reference.',
    inputYaml: `apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: payments-events-topic
  namespace: payments-team
spec:
  name: payments.events.v1
  serviceRef: payments-admin
  applicationServiceRef: payments-service
  partitions: 12
  replicationFactor: 3
  config:
    retention.ms: "604800000"
    cleanup.policy: delete
    min.insync.replicas: "2"`,
    outputYaml: `apiVersion: kafka/v2
kind: Topic
metadata:
  name: payments.events.v1
  cluster: payments-prod-vcluster
spec:
  partitions: 12
  replicationFactor: 3
  configs:
    retention.ms: "604800000"
    cleanup.policy: delete
    min.insync.replicas: "2"`,
    mappings: [
      { from: 'spec.name', to: 'metadata.name', note: 'Topic name' },
      { from: 'spec.serviceRef → clusterRef', to: 'metadata.cluster', note: 'Resolved via ServiceAccount lookup' },
      { from: 'spec.partitions', to: 'spec.partitions', note: 'Direct mapping' },
      { from: 'spec.replicationFactor', to: 'spec.replicationFactor', note: 'Direct mapping' },
      { from: 'spec.config', to: 'spec.configs', note: 'Kafka configs preserved' }
    ]
  }
]

function TransformationCard({ transformation }) {
  const Icon = transformation.icon
  const colorClasses = {
    purple: 'bg-purple-500/20 text-purple-400 border-purple-500/30',
    green: 'bg-green-500/20 text-green-400 border-green-500/30',
    orange: 'bg-orange-500/20 text-orange-400 border-orange-500/30'
  }

  return (
    <div className="bg-gray-900 rounded-xl border border-gray-800 overflow-hidden">
      <div className={`px-6 py-4 border-b border-gray-800 ${colorClasses[transformation.color]}`}>
        <div className="flex items-center gap-3">
          <Icon size={24} />
          <h3 className="text-xl font-bold text-white">{transformation.name}</h3>
        </div>
        <p className="text-gray-300 mt-2">{transformation.description}</p>
      </div>

      <div className="grid lg:grid-cols-2 gap-0 lg:divide-x divide-gray-800">
        <div className="p-4">
          <div className="flex items-center gap-2 text-sm text-gray-400 mb-3">
            <span className="px-2 py-1 bg-blue-500/20 text-blue-400 rounded">INPUT</span>
            <span>Internal CRD</span>
          </div>
          <CodeBlock code={transformation.inputYaml} language="yaml" />
        </div>

        <div className="p-4 border-t lg:border-t-0 border-gray-800">
          <div className="flex items-center gap-2 text-sm text-gray-400 mb-3">
            <span className="px-2 py-1 bg-green-500/20 text-green-400 rounded">OUTPUT</span>
            <span>Conduktor Gateway</span>
          </div>
          <CodeBlock code={transformation.outputYaml} language="yaml" />
        </div>
      </div>

      <div className="px-6 py-4 bg-gray-950 border-t border-gray-800">
        <h4 className="text-sm font-semibold text-gray-400 mb-3">Field Mappings</h4>
        <div className="space-y-2">
          {transformation.mappings.map((mapping, idx) => (
            <div key={idx} className="flex items-center gap-3 text-sm">
              <code className="px-2 py-1 bg-gray-800 rounded text-blue-400 font-mono text-xs">
                {mapping.from}
              </code>
              <ArrowRight size={14} className="text-gray-600" />
              <code className="px-2 py-1 bg-gray-800 rounded text-green-400 font-mono text-xs">
                {mapping.to}
              </code>
              <span className="text-gray-500 text-xs">{mapping.note}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function ArchitectureDiagram() {
  return (
    <div className="bg-gray-900 rounded-xl border border-gray-800 p-6">
      <div className="flex flex-col lg:flex-row items-center justify-center gap-4 lg:gap-8">
        {/* Internal CRDs */}
        <div className="flex flex-col items-center gap-2">
          <div className="text-sm text-gray-400 mb-2">Kubernetes CRDs</div>
          <div className="flex flex-col gap-2">
            <div className="px-4 py-2 bg-blue-500/20 text-blue-400 rounded-lg border border-blue-500/30 text-sm">
              KafkaCluster
            </div>
            <div className="px-4 py-2 bg-green-500/20 text-green-400 rounded-lg border border-green-500/30 text-sm">
              ServiceAccount
            </div>
            <div className="px-4 py-2 bg-orange-500/20 text-orange-400 rounded-lg border border-orange-500/30 text-sm">
              Topic
            </div>
          </div>
        </div>

        {/* Transformer */}
        <div className="flex flex-col items-center gap-2">
          <ArrowRight size={24} className="text-gray-600 rotate-90 lg:rotate-0" />
          <div className="px-6 py-4 bg-purple-500/20 text-purple-400 rounded-xl border border-purple-500/30 text-center">
            <RefreshCw size={24} className="mx-auto mb-2" />
            <div className="font-semibold">Transformer</div>
            <div className="text-xs text-gray-400 mt-1">Java Classes</div>
          </div>
          <ArrowRight size={24} className="text-gray-600 rotate-90 lg:rotate-0" />
        </div>

        {/* Conduktor CRDs */}
        <div className="flex flex-col items-center gap-2">
          <div className="text-sm text-gray-400 mb-2">Conduktor Gateway</div>
          <div className="flex flex-col gap-2">
            <div className="px-4 py-2 bg-purple-500/20 text-purple-400 rounded-lg border border-purple-500/30 text-sm">
              VirtualCluster
            </div>
            <div className="px-4 py-2 bg-green-500/20 text-green-400 rounded-lg border border-green-500/30 text-sm">
              GatewayServiceAccount
            </div>
            <div className="px-4 py-2 bg-orange-500/20 text-orange-400 rounded-lg border border-orange-500/30 text-sm">
              Topic (kafka/v2)
            </div>
          </div>
        </div>

        {/* CLI */}
        <div className="flex flex-col items-center gap-2">
          <ArrowRight size={24} className="text-gray-600 rotate-90 lg:rotate-0" />
          <div className="px-6 py-4 bg-cyan-500/20 text-cyan-400 rounded-xl border border-cyan-500/30 text-center">
            <Terminal size={24} className="mx-auto mb-2" />
            <div className="font-semibold">Conduktor CLI</div>
            <div className="text-xs text-gray-400 mt-1">--dry-run</div>
          </div>
          <ArrowRight size={24} className="text-gray-600 rotate-90 lg:rotate-0" />
        </div>

        {/* Result */}
        <div className="flex flex-col items-center gap-2">
          <div className="px-6 py-4 bg-emerald-500/20 text-emerald-400 rounded-xl border border-emerald-500/30 text-center">
            <CheckCircle size={24} className="mx-auto mb-2" />
            <div className="font-semibold">Validated</div>
            <div className="text-xs text-gray-400 mt-1">No side effects</div>
          </div>
        </div>
      </div>
    </div>
  )
}

const cliExample = `# Validate transformed resources with Conduktor CLI
$ conduktor apply -f virtual-cluster.yaml --dry-run
✓ VirtualCluster "payments-prod-vcluster" validated

$ conduktor apply -f service-account.yaml --dry-run
✓ GatewayServiceAccount "payments-admin" validated

$ conduktor apply -f topic.yaml --dry-run
✓ Topic "payments.events.v1" validated

# Apply when ready (no --dry-run)
$ conduktor apply -f virtual-cluster.yaml
VirtualCluster "payments-prod-vcluster" created`

const transformerExample = `# Input: Internal Kubernetes CRD
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: payments-events
  namespace: payments-team
spec:
  name: payments.events.v1
  serviceRef: payments-admin    # Resolved to cluster
  partitions: 12
---
# Output: Conduktor Gateway Format
apiVersion: kafka/v2
kind: Topic
metadata:
  name: payments.events.v1
  cluster: payments-prod-vcluster  # From ServiceAccount lookup
spec:
  partitions: 12
  replicationFactor: 3`

export default function TransformationPage() {
  return (
    <PageLayout
      title="CRD Transformation"
      subtitle="Transform internal Kubernetes CRDs to Conduktor Gateway format"
      icon={RefreshCw}
      aphorism={{
        text: "The art of programming is the art of organizing complexity.",
        author: "Edsger W. Dijkstra"
      }}
    >
      <Section title="Architecture Overview" icon={RefreshCw}>
        <p className="text-gray-300 mb-6">
          The transformation layer converts internal Kubernetes CRDs into Conduktor Gateway format,
          enabling validation through the Conduktor CLI without creating actual resources.
          This provides a safety net before resources are applied to the Gateway.
        </p>
        <ArchitectureDiagram />
      </Section>

      <Section title="Transformation Details" icon={ArrowRight}>
        <div className="space-y-8">
          {transformations.map((t, idx) => (
            <TransformationCard key={idx} transformation={t} />
          ))}
        </div>
      </Section>

      <Section title="Implementation" icon={Terminal}>
        <CardGrid>
          <Card
            title="Transformer Pattern"
            icon={RefreshCw}
            color="purple"
          >
            <p className="text-gray-400 text-sm mb-4">
              Each transformer implements a generic interface for type-safe conversions.
              The TopicTransformer demonstrates cluster resolution via CRDStore lookup.
            </p>
            <CodeBlock code={transformerExample} language="yaml" />
          </Card>

          <Card
            title="CLI Integration"
            icon={Terminal}
            color="cyan"
          >
            <p className="text-gray-400 text-sm mb-4">
              The ConduktorCli class executes dry-run validation, writing temporary YAML files
              and capturing CLI output for validation feedback.
            </p>
            <CodeBlock code={cliExample} language="bash" />
          </Card>
        </CardGrid>
      </Section>

      <Section title="Key Features" icon={CheckCircle}>
        <CardGrid cols={3}>
          <Card title="Type Safety" icon={CheckCircle} color="green">
            <ul className="text-gray-400 text-sm space-y-2">
              <li>• Generic transformer interface</li>
              <li>• Strongly typed models</li>
              <li>• Compile-time validation</li>
            </ul>
          </Card>
          <Card title="DN Extraction" icon={Users} color="blue">
            <ul className="text-gray-400 text-sm space-y-2">
              <li>• Parses X.509 DNs</li>
              <li>• Extracts CN components</li>
              <li>• Preserves full DN if no CN</li>
            </ul>
          </Card>
          <Card title="Dry-Run Only" icon={Terminal} color="orange">
            <ul className="text-gray-400 text-sm space-y-2">
              <li>• No side effects</li>
              <li>• Validation feedback</li>
              <li>• Safe to run repeatedly</li>
            </ul>
          </Card>
        </CardGrid>
      </Section>
    </PageLayout>
  )
}
