import { Server, Cloud, Monitor, RefreshCw, Shield, Database, Settings, CheckCircle2 } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import DiagramBox, { DiagramNode, DiagramArrow } from '../components/DiagramBox'
import Section from '../components/Section'
import CodeBlock from '../components/CodeBlock'

const dockerServices = [
  { port: 8080, service: 'Console UI', description: 'Conduktor Console web interface' },
  { port: 8888, service: 'Gateway Admin API', description: 'HTTP API for gateway management' },
  { port: 6969, service: 'Gateway Kafka', description: 'Kafka protocol with mTLS' },
  { port: 8081, service: 'Schema Registry', description: 'Avro/Protobuf schema management' },
]

const helmValues = `# values-minikube.yaml
replicaCount: 2

image:
  repository: messaging-operator
  tag: latest
  pullPolicy: Never  # Use local image

webhook:
  port: 8443

tls:
  caCert: <base64-encoded-ca-cert>

resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
  limits:
    memory: "512Mi"
    cpu: "500m"

# Namespace created externally
createNamespace: false`

const minikubeCommands = `# Start Minikube cluster
./functional-tests/setup-minikube.sh

# Deploy operator
./functional-tests/deploy.sh

# Run E2E tests
./functional-tests/run-tests.sh

# With fresh cluster and cleanup
./functional-tests/run-tests.sh --fresh-cluster --cleanup

# Check webhook pods
kubectl get pods -n operator-system

# View webhook logs
kubectl logs -l app.kubernetes.io/component=webhook -n operator-system`

export default function OperationsPage() {
  return (
    <PageLayout
      title="Operations"
      subtitle="Deploy, monitor, and manage the operator in Kubernetes"
      icon={Server}
      breadcrumb="Operations"
      aphorism={{
        text: "It is not enough to do your best; you must know what to do, and then do your best.",
        author: "W. Edwards Deming"
      }}
    >
      {/* Deployment Architecture */}
      <Section title="Deployment Architecture" subtitle="How components are deployed in Kubernetes">
        <DiagramBox title="Kubernetes Deployment">
          <div className="py-6">
            <div className="flex flex-col lg:flex-row items-center justify-center gap-8">
              {/* Namespace box */}
              <div className="border-2 border-dashed border-blue-300 dark:border-blue-700 rounded-2xl p-6 bg-blue-50/50 dark:bg-blue-900/10">
                <div className="text-sm font-semibold text-blue-600 dark:text-blue-400 mb-4">operator-system namespace</div>

                <div className="space-y-4">
                  {/* Deployment */}
                  <div className="border border-gray-200 dark:border-gray-700 rounded-xl p-4 bg-white dark:bg-gray-900">
                    <div className="text-xs text-gray-500 mb-2">Deployment</div>
                    <DiagramNode color="purple" className="mb-2">
                      messaging-operator-webhook
                    </DiagramNode>
                    <div className="flex gap-2 justify-center">
                      <span className="px-2 py-1 bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400 rounded text-xs">Pod 1</span>
                      <span className="px-2 py-1 bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400 rounded text-xs">Pod 2</span>
                    </div>
                  </div>

                  {/* Service */}
                  <div className="border border-gray-200 dark:border-gray-700 rounded-xl p-4 bg-white dark:bg-gray-900">
                    <div className="text-xs text-gray-500 mb-2">Service</div>
                    <DiagramNode color="blue">
                      messaging-operator-webhook:8443
                    </DiagramNode>
                  </div>

                  {/* Secret */}
                  <div className="border border-gray-200 dark:border-gray-700 rounded-xl p-4 bg-white dark:bg-gray-900">
                    <div className="text-xs text-gray-500 mb-2">Secret</div>
                    <DiagramNode color="orange">
                      webhook-tls-secret
                    </DiagramNode>
                  </div>
                </div>
              </div>

              {/* Arrow */}
              <div className="hidden lg:block">
                <DiagramArrow direction="right" label="admission review" />
              </div>

              {/* API Server */}
              <div className="border-2 border-gray-300 dark:border-gray-600 rounded-2xl p-6 bg-gray-50 dark:bg-gray-900">
                <DiagramNode color="gray">
                  <div className="font-bold">Kubernetes API Server</div>
                  <div className="text-xs opacity-70 mt-1">ValidatingWebhookConfiguration</div>
                </DiagramNode>
              </div>
            </div>
          </div>
        </DiagramBox>
      </Section>

      {/* Minikube Setup */}
      <Section title="Local Development (Minikube)" subtitle="Setting up a local Kubernetes cluster">
        <CardGrid cols={2}>
          <div>
            <h4 className="font-semibold mb-4">Setup Scripts</h4>
            <CodeBlock
              title="Terminal"
              code={minikubeCommands}
            />
          </div>
          <div>
            <h4 className="font-semibold mb-4">What the scripts do</h4>
            <div className="space-y-4">
              <Card>
                <div className="flex items-start gap-3">
                  <div className="w-6 h-6 bg-blue-500 text-white rounded flex items-center justify-center text-sm font-bold">1</div>
                  <div>
                    <div className="font-medium">setup-minikube.sh</div>
                    <p className="text-sm text-gray-500">Creates Minikube cluster, installs CRDs, creates namespace</p>
                  </div>
                </div>
              </Card>
              <Card>
                <div className="flex items-start gap-3">
                  <div className="w-6 h-6 bg-blue-500 text-white rounded flex items-center justify-center text-sm font-bold">2</div>
                  <div>
                    <div className="font-medium">deploy.sh</div>
                    <p className="text-sm text-gray-500">Generates TLS certs, builds Docker image, deploys Helm chart</p>
                  </div>
                </div>
              </Card>
              <Card>
                <div className="flex items-start gap-3">
                  <div className="w-6 h-6 bg-blue-500 text-white rounded flex items-center justify-center text-sm font-bold">3</div>
                  <div>
                    <div className="font-medium">run-tests.sh</div>
                    <p className="text-sm text-gray-500">Runs Bats E2E tests against the deployed operator</p>
                  </div>
                </div>
              </Card>
            </div>
          </div>
        </CardGrid>
      </Section>

      {/* Helm Chart */}
      <Section title="Helm Chart Configuration" subtitle="Key values for deployment">
        <CodeBlock
          title="values-minikube.yaml"
          language="yaml"
          code={helmValues}
        />
      </Section>

      {/* Docker Container */}
      <Section title="Docker Container Services" subtitle="For local development with Conduktor">
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 dark:bg-gray-800">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold">Port</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">Service</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">Description</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
              {dockerServices.map((service) => (
                <tr key={service.port}>
                  <td className="px-6 py-4">
                    <code className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-sm">:{service.port}</code>
                  </td>
                  <td className="px-6 py-4 font-medium">{service.service}</td>
                  <td className="px-6 py-4 text-gray-600 dark:text-gray-400">{service.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-6 grid md:grid-cols-2 gap-6">
          <Card icon={Monitor} title="Console Access">
            <div className="mt-4 space-y-2 text-sm">
              <div><span className="text-gray-500">URL:</span> <code>http://localhost:8080</code></div>
              <div><span className="text-gray-500">Login:</span> <code>admin@demo.dev</code></div>
              <div><span className="text-gray-500">Password:</span> <code>123_ABC_abc</code></div>
            </div>
          </Card>
          <Card icon={Settings} title="Make Commands">
            <CodeBlock
              code={`make all     # Full build and run
make build   # Build Docker image
make run     # Start container
make rm      # Stop container
make logs    # Follow logs`}
              className="mt-4"
            />
          </Card>
        </div>
      </Section>

      {/* Health Checks */}
      <Section title="Health Checks & Monitoring">
        <CardGrid cols={2}>
          <Card icon={CheckCircle2} title="Readiness Probe">
            <div className="mt-4">
              <code className="text-sm">GET /health</code>
              <p className="text-sm text-gray-500 mt-2">Returns 200 OK when webhook is ready to accept requests</p>
            </div>
          </Card>
          <Card icon={RefreshCw} title="Liveness Probe">
            <div className="mt-4">
              <code className="text-sm">GET /health</code>
              <p className="text-sm text-gray-500 mt-2">Same endpoint, used for container restart decisions</p>
            </div>
          </Card>
        </CardGrid>

        <div className="mt-6">
          <h4 className="font-semibold mb-3">Useful kubectl commands</h4>
          <CodeBlock
            code={`# Check pod status
kubectl get pods -n operator-system -l app.kubernetes.io/component=webhook

# View logs
kubectl logs -f deployment/messaging-operator-webhook -n operator-system

# Check endpoints
kubectl get endpoints messaging-operator-webhook -n operator-system

# Describe webhook configuration
kubectl get validatingwebhookconfiguration messaging-operator-webhook -o yaml

# Test health endpoint (from inside cluster)
kubectl run -it --rm debug --image=curlimages/curl -- curl -k https://messaging-operator-webhook.operator-system:8443/health`}
          />
        </div>
      </Section>

      {/* Troubleshooting */}
      <Section title="Troubleshooting" subtitle="Common issues and solutions">
        <div className="space-y-4">
          {[
            {
              issue: 'Pod stuck in ImagePullBackOff',
              solution: 'Ensure image is built with Minikube Docker daemon: eval $(minikube -p <profile> docker-env)'
            },
            {
              issue: 'Webhook timeout errors',
              solution: 'Check if pods are running and service endpoints are populated'
            },
            {
              issue: 'TLS certificate errors',
              solution: 'Regenerate certificates and update the webhook configuration caBundle'
            },
            {
              issue: 'Admission rejected unexpectedly',
              solution: 'Check webhook logs for detailed validation error messages'
            },
          ].map((item, index) => (
            <Card key={index} variant="warning">
              <div className="font-semibold mb-2">{item.issue}</div>
              <p className="text-sm text-gray-600 dark:text-gray-400">{item.solution}</p>
            </Card>
          ))}
        </div>
      </Section>
    </PageLayout>
  )
}
