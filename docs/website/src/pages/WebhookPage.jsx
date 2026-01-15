import { Lock, Shield, CheckCircle2, XCircle, AlertTriangle, Server, Code2 } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import DiagramBox, { DiagramNode, DiagramArrow } from '../components/DiagramBox'
import Section from '../components/Section'
import CodeBlock from '../components/CodeBlock'

const endpoints = [
  { path: '/health', method: 'GET', description: 'Health check endpoint', returns: '200 OK' },
  { path: '/validate/topic', method: 'POST', description: 'Topic admission validation', returns: 'AdmissionReview' },
  { path: '/validate/acl', method: 'POST', description: 'ACL admission validation', returns: 'AdmissionReview' },
  { path: '/validate/serviceaccount', method: 'POST', description: 'ServiceAccount admission validation', returns: 'AdmissionReview' },
  { path: '/validate/kafkacluster', method: 'POST', description: 'KafkaCluster admission validation', returns: 'AdmissionReview' },
  { path: '/validate/consumergroup', method: 'POST', description: 'ConsumerGroup admission validation', returns: 'AdmissionReview' },
]

const validationChecks = [
  { check: 'Resource exists', description: 'Referenced resources must exist in the cluster' },
  { check: 'Same owner', description: 'All referenced resources must have the same applicationServiceRef' },
  { check: 'Owner immutability', description: 'applicationServiceRef cannot be changed after creation' },
  { check: 'Valid references', description: 'Referenced field names must point to valid resources' },
  { check: 'No circular refs', description: 'Resource graph must be acyclic' },
]

export default function WebhookPage() {
  return (
    <PageLayout
      title="Webhook Deep Dive"
      subtitle="Understanding the ValidatingAdmissionWebhook implementation"
      icon={Lock}
      breadcrumb="Webhook"
      aphorism={{
        text: "Quis custodiet ipsos custodes? (Who watches the watchmen?)",
        author: "Juvenal"
      }}
    >
      {/* What is it */}
      <Section title="What is a ValidatingWebhook?" subtitle="Kubernetes admission control explained">
        <Card>
          <p className="text-gray-600 dark:text-gray-400">
            A ValidatingAdmissionWebhook intercepts requests to the Kubernetes API server
            <strong> before </strong> resources are persisted. It can inspect, validate, and
            <strong> reject </strong> requests that don't meet certain criteria. Unlike
            MutatingWebhooks, ValidatingWebhooks cannot modify resources — they can only
            accept or reject them.
          </p>
        </Card>
      </Section>

      {/* Flow Diagram */}
      <Section title="Request Flow" subtitle="How requests are validated">
        <DiagramBox title="Admission Control Flow">
          <div className="py-8">
            <div className="flex flex-col items-center space-y-4">
              {/* kubectl */}
              <DiagramNode color="gray" className="w-64">
                <div className="font-bold">kubectl apply</div>
                <div className="text-xs opacity-70">User submits Topic YAML</div>
              </DiagramNode>

              <DiagramArrow direction="down" label="HTTP request" />

              {/* API Server */}
              <DiagramNode color="blue" className="w-64">
                <div className="font-bold">API Server</div>
                <div className="text-xs opacity-70">Receives CREATE request</div>
              </DiagramNode>

              <DiagramArrow direction="down" label="AdmissionReview" />

              {/* Webhook */}
              <DiagramNode color="purple" className="w-64">
                <div className="font-bold">ValidatingWebhook</div>
                <div className="text-xs opacity-70">Runs validation logic</div>
              </DiagramNode>

              <div className="flex gap-16 mt-4">
                <div className="flex flex-col items-center">
                  <DiagramArrow direction="down" />
                  <DiagramNode color="green" className="w-32">
                    <CheckCircle2 className="mx-auto mb-1" size={20} />
                    <div className="text-sm">Allowed</div>
                  </DiagramNode>
                  <span className="text-xs text-gray-500 mt-2">Resource created</span>
                </div>
                <div className="flex flex-col items-center">
                  <DiagramArrow direction="down" />
                  <DiagramNode color="red" className="w-32">
                    <XCircle className="mx-auto mb-1" size={20} />
                    <div className="text-sm">Denied</div>
                  </DiagramNode>
                  <span className="text-xs text-gray-500 mt-2">Error returned</span>
                </div>
              </div>
            </div>
          </div>
        </DiagramBox>
      </Section>

      {/* Endpoints */}
      <Section title="Webhook Endpoints" subtitle="HTTPS endpoints exposed on port 8443">
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 dark:bg-gray-800">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold">Path</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">Method</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">Description</th>
                <th className="px-6 py-3 text-left text-sm font-semibold">Returns</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
              {endpoints.map((ep) => (
                <tr key={ep.path}>
                  <td className="px-6 py-4">
                    <code className="text-sm">{ep.path}</code>
                  </td>
                  <td className="px-6 py-4">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${
                      ep.method === 'GET' ? 'bg-green-100 text-green-700' : 'bg-blue-100 text-blue-700'
                    }`}>
                      {ep.method}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-gray-600 dark:text-gray-400">{ep.description}</td>
                  <td className="px-6 py-4 text-gray-600 dark:text-gray-400">{ep.returns}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      {/* Validation Checks */}
      <Section title="Validation Checks" subtitle="What the webhook validates">
        <CardGrid cols={2}>
          {validationChecks.map((item, index) => (
            <Card key={index}>
              <div className="flex items-start gap-3">
                <CheckCircle2 className="text-green-500 shrink-0 mt-0.5" size={20} />
                <div>
                  <div className="font-semibold">{item.check}</div>
                  <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{item.description}</p>
                </div>
              </div>
            </Card>
          ))}
        </CardGrid>
      </Section>

      {/* Code Architecture */}
      <Section title="Code Architecture" subtitle="How the webhook is implemented">
        <CardGrid cols={2}>
          <Card icon={Server} title="WebhookApplication.java">
            <p className="text-sm text-gray-600 dark:text-gray-400 mt-2">
              Main entry point. Creates HTTPS server with TLS certificates,
              registers endpoints, and starts listening on port 8443.
            </p>
            <CodeBlock
              className="mt-4"
              code={`// Creates HTTPS server with TLS
HttpsServer server = createHttpsServer(
    port, certPath, keyPath
);

// Registers validation endpoints
webhookHandler.registerEndpoints();

server.start();`}
            />
          </Card>
          <Card icon={Shield} title="WebhookValidator.java">
            <p className="text-sm text-gray-600 dark:text-gray-400 mt-2">
              Core validation logic. Parses AdmissionReview requests,
              runs ownership validation, and returns allow/deny responses.
            </p>
            <CodeBlock
              className="mt-4"
              code={`// Validate the resource
ValidationResult result = ownershipValidator
    .validate(resource, operation);

// Return AdmissionReview response
return result.isValid()
    ? allowResponse(uid)
    : denyResponse(uid, result.getMessage());`}
            />
          </Card>
          <Card icon={Code2} title="OwnershipValidator.java">
            <p className="text-sm text-gray-600 dark:text-gray-400 mt-2">
              Enforces ownership chains. Checks that all referenced resources
              belong to the same ApplicationService.
            </p>
            <CodeBlock
              className="mt-4"
              code={`// Check owner matches
if (!resource.getAppServiceRef()
    .equals(referenced.getAppServiceRef())) {
    return ValidationResult.invalid(
        "Different applicationServiceRef"
    );
}`}
            />
          </Card>
          <Card icon={Lock} title="CRDStore.java">
            <p className="text-sm text-gray-600 dark:text-gray-400 mt-2">
              Thread-safe in-memory storage. Provides lookups for reference
              validation and publishes events for observability.
            </p>
            <CodeBlock
              className="mt-4"
              code={`// Thread-safe storage
private final ConcurrentMap<CRDKind,
    ConcurrentMap<String, Object>> store;

// Event publishing
eventPublisher.publish(
    ReconciliationEvent.before(CREATE, resource)
);`}
            />
          </Card>
        </CardGrid>
      </Section>

      {/* TLS Configuration */}
      <Section title="TLS Configuration" subtitle="Secure communication with the API server">
        <Card>
          <div className="grid md:grid-cols-2 gap-8">
            <div>
              <h4 className="font-semibold mb-4">Environment Variables</h4>
              <div className="space-y-3">
                {[
                  { name: 'WEBHOOK_PORT', default: '8443', desc: 'HTTPS server port' },
                  { name: 'TLS_CERT_PATH', default: '/etc/webhook/certs/tls.crt', desc: 'Server certificate' },
                  { name: 'TLS_KEY_PATH', default: '/etc/webhook/certs/tls.key', desc: 'Private key' },
                ].map((env) => (
                  <div key={env.name} className="p-3 bg-gray-50 dark:bg-gray-800 rounded-lg">
                    <code className="text-sm text-blue-600 dark:text-blue-400">{env.name}</code>
                    <div className="text-xs text-gray-500 mt-1">Default: {env.default}</div>
                    <div className="text-sm text-gray-600 dark:text-gray-400">{env.desc}</div>
                  </div>
                ))}
              </div>
            </div>
            <div>
              <h4 className="font-semibold mb-4">Certificate Requirements</h4>
              <ul className="space-y-2 text-sm">
                {[
                  'Server certificate must be signed by a CA',
                  'CA certificate must be base64-encoded in webhook config',
                  'Certificate CN should be <= 64 characters',
                  'SANs should include service DNS name',
                ].map((req, i) => (
                  <li key={i} className="flex items-start gap-2">
                    <CheckCircle2 size={16} className="text-green-500 shrink-0 mt-0.5" />
                    <span className="text-gray-600 dark:text-gray-400">{req}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </Card>
      </Section>

      {/* Error Messages */}
      <Section title="Error Messages" subtitle="Common rejection reasons">
        <div className="space-y-4">
          {[
            { error: 'applicationServiceRef cannot be changed', cause: 'Trying to modify the owner reference on update' },
            { error: 'Referenced ServiceAccount has different applicationServiceRef', cause: 'Cross-tenant reference detected' },
            { error: 'Referenced resource does not exist', cause: 'Referencing a non-existent resource' },
            { error: 'Missing required field: applicationServiceRef', cause: 'Resource missing owner reference' },
          ].map((item, i) => (
            <Card key={i} variant="error">
              <div className="flex items-start gap-3">
                <AlertTriangle className="text-red-500 shrink-0" size={20} />
                <div>
                  <code className="text-sm text-red-600 dark:text-red-400">{item.error}</code>
                  <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{item.cause}</p>
                </div>
              </div>
            </Card>
          ))}
        </div>
      </Section>
    </PageLayout>
  )
}
