import { Terminal, Key, Database, Shield, CheckCircle2, ArrowRight, Server, Lock } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import DiagramBox, { DiagramNode, DiagramArrow } from '../components/DiagramBox'
import Section from '../components/Section'
import CodeBlock from '../components/CodeBlock'

const authFlow = [
  { step: 1, title: 'Bootstrap Script', description: 'Authenticates to Console /api/login endpoint' },
  { step: 2, title: 'Token Retrieval', description: 'Extracts access_token from JSON response' },
  { step: 3, title: 'Secret Creation', description: 'Stores credentials in Kubernetes Secret' },
  { step: 4, title: 'Operator Mount', description: 'Operator pod mounts Secret as env vars or files' },
  { step: 5, title: 'CLI Execution', description: 'Operator executes conduktor apply with credentials' },
]

const secretKeys = [
  { key: 'console-url', description: 'Console API URL', example: 'http://console:8080' },
  { key: 'console-token', description: 'Bearer token for Console authentication', example: 'eyJhbGciOiJIUzI1NiIs...' },
  { key: 'gateway-url', description: 'Gateway Admin API URL', example: 'http://gateway:8888' },
  { key: 'gateway-user', description: 'Gateway Basic Auth username', example: 'admin' },
  { key: 'gateway-password', description: 'Gateway Basic Auth password', example: 'conduktor' },
]

const bootstrapScript = `#!/bin/bash
# Bootstrap script for Conduktor CLI credentials

# Configuration (with defaults)
CONSOLE_URL="\${CONSOLE_URL:-http://localhost:8080}"
CONSOLE_USER="\${CONSOLE_USER:-admin@demo.dev}"
CONSOLE_PASSWORD="\${CONSOLE_PASSWORD:-123_ABC_abc}"
GATEWAY_URL="\${GATEWAY_URL:-http://localhost:8888}"
SECRET_NAME="\${SECRET_NAME:-conduktor-cli-credentials}"

# 1. Wait for Console to be ready
wait_for_console() {
    curl -sf "\${CONSOLE_URL}/platform/api/modules/resources/health/live"
}

# 2. Authenticate and get token
TOKEN=$(curl -sf -X POST "\${CONSOLE_URL}/api/login" \\
    -H "Content-Type: application/json" \\
    -d "{\\"username\\":\\"\${CONSOLE_USER}\\",\\"password\\":\\"\${CONSOLE_PASSWORD}\\"}" \\
    | jq -r '.access_token')

# 3. Create Kubernetes Secret
kubectl create secret generic "\$SECRET_NAME" \\
    --from-literal=console-url="\$CONSOLE_URL" \\
    --from-literal=console-token="\$TOKEN" \\
    --from-literal=gateway-url="\$GATEWAY_URL" \\
    --from-literal=gateway-user="admin" \\
    --from-literal=gateway-password="conduktor" \\
    --dry-run=client -o yaml | kubectl apply -f -`

const secretManifest = `apiVersion: v1
kind: Secret
metadata:
  name: conduktor-cli-credentials
  namespace: default
  labels:
    app.kubernetes.io/name: conduktor-cli
    app.kubernetes.io/component: credentials
type: Opaque
stringData:
  console-url: "http://console:8080"
  console-token: "<populated-by-bootstrap-script>"
  gateway-url: "http://gateway:8888"
  gateway-user: "admin"
  gateway-password: "conduktor"`

const javaCredentials = `public class ConduktorCliCredentials {
    private static final String DEFAULT_SECRET_MOUNT_PATH = "/var/run/secrets/conduktor";

    // Load credentials from env vars or mounted secret files
    public static ConduktorCliCredentials load(String secretMountPath) {
        String consoleUrl = loadValue("CONDUKTOR_CONSOLE_URL", secretMountPath, "console-url");
        String consoleToken = loadValue("CONDUKTOR_CONSOLE_TOKEN", secretMountPath, "console-token");
        String gatewayUrl = loadValue("CONDUKTOR_GATEWAY_URL", secretMountPath, "gateway-url");
        String gatewayUser = loadValue("CONDUKTOR_GATEWAY_USER", secretMountPath, "gateway-user");
        String gatewayPassword = loadValue("CONDUKTOR_GATEWAY_PASSWORD", secretMountPath, "gateway-password");

        return new ConduktorCliCredentials(consoleUrl, consoleToken, gatewayUrl, gatewayUser, gatewayPassword);
    }

    private static String loadValue(String envVar, String secretMountPath, String secretKey) {
        // First try environment variable
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        // Then try mounted secret file
        Path secretFile = Path.of(secretMountPath, secretKey);
        if (Files.exists(secretFile)) {
            return Files.readString(secretFile).trim();
        }
        throw new IllegalStateException("Missing credential: " + envVar);
    }
}`

const cliExecution = `public class ConduktorCli {
    private final ConduktorCliCredentials credentials;

    public CliResult apply(ConduktorResource<?> resource) {
        // Write resource to temp YAML file
        Path yamlFile = yamlWriter.writeToTempFile(resource);

        // Build command: conduktor apply -f <file>
        List<String> command = List.of(cliPath, "apply", "-f", yamlFile.toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        Map<String, String> env = pb.environment();

        // Set authentication environment variables
        env.put("CDK_BASE_URL", credentials.getConsoleUrl());
        env.put("CDK_TOKEN", credentials.getConsoleToken());
        env.put("CDK_GATEWAY_BASE_URL", credentials.getGatewayUrl());
        env.put("CDK_GATEWAY_USER", credentials.getGatewayUser());
        env.put("CDK_GATEWAY_PASSWORD", credentials.getGatewayPassword());

        // Execute and return result
        Process process = pb.start();
        return new CliResult(process.exitValue(), stdout, stderr);
    }
}`

const deploymentEnvVars = `# Operator Deployment with Secret as environment variables
apiVersion: apps/v1
kind: Deployment
metadata:
  name: messaging-operator
spec:
  template:
    spec:
      containers:
        - name: operator
          env:
            - name: CONDUKTOR_CONSOLE_URL
              valueFrom:
                secretKeyRef:
                  name: conduktor-cli-credentials
                  key: console-url
            - name: CONDUKTOR_CONSOLE_TOKEN
              valueFrom:
                secretKeyRef:
                  name: conduktor-cli-credentials
                  key: console-token
            - name: CONDUKTOR_GATEWAY_URL
              valueFrom:
                secretKeyRef:
                  name: conduktor-cli-credentials
                  key: gateway-url
            - name: CONDUKTOR_GATEWAY_USER
              valueFrom:
                secretKeyRef:
                  name: conduktor-cli-credentials
                  key: gateway-user
            - name: CONDUKTOR_GATEWAY_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: conduktor-cli-credentials
                  key: gateway-password`

const deploymentVolume = `# Operator Deployment with Secret as mounted volume
apiVersion: apps/v1
kind: Deployment
metadata:
  name: messaging-operator
spec:
  template:
    spec:
      containers:
        - name: operator
          volumeMounts:
            - name: conduktor-credentials
              mountPath: /var/run/secrets/conduktor
              readOnly: true
      volumes:
        - name: conduktor-credentials
          secret:
            secretName: conduktor-cli-credentials`

export default function CLIIntegrationPage() {
  return (
    <PageLayout
      title="CLI Integration"
      subtitle="Authenticate and apply resources to Console and Gateway"
      icon={Terminal}
      breadcrumb="CLI Integration"
      aphorism={{
        text: "The best password is the one you don't have to remember.",
        author: "Security Wisdom"
      }}
    >
      {/* Overview */}
      <Section title="Overview" subtitle="How the operator authenticates with Conduktor services">
        <p className="text-gray-600 dark:text-gray-400 mb-6">
          The operator uses the <code className="text-blue-500">conduktor</code> CLI to apply transformed resources
          to Console and Gateway. Authentication requires a bearer token for Console and Basic Auth for Gateway,
          both stored securely in a Kubernetes Secret.
        </p>

        <DiagramBox title="Authentication Flow">
          <div className="flex items-center justify-between flex-wrap gap-4">
            <DiagramNode icon={Terminal} label="Bootstrap Script" color="yellow" />
            <DiagramArrow label="POST /api/login" />
            <DiagramNode icon={Server} label="Console" color="blue" />
            <DiagramArrow label="access_token" />
            <DiagramNode icon={Key} label="K8s Secret" color="green" />
            <DiagramArrow label="mount" />
            <DiagramNode icon={Shield} label="Operator" color="purple" />
          </div>
        </DiagramBox>
      </Section>

      {/* Authentication Flow Steps */}
      <Section title="Authentication Flow" subtitle="Step-by-step credential setup">
        <div className="space-y-4">
          {authFlow.map((item, index) => (
            <div key={item.step} className="flex items-start gap-4 p-4 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700">
              <div className="flex-shrink-0 w-10 h-10 rounded-full bg-blue-500 text-white flex items-center justify-center font-bold">
                {item.step}
              </div>
              <div className="flex-1">
                <h4 className="font-semibold text-gray-900 dark:text-gray-100">{item.title}</h4>
                <p className="text-gray-600 dark:text-gray-400 text-sm mt-1">{item.description}</p>
              </div>
              {index < authFlow.length - 1 && (
                <ArrowRight className="text-gray-400 flex-shrink-0 mt-2" size={20} />
              )}
            </div>
          ))}
        </div>
      </Section>

      {/* Bootstrap Script */}
      <Section title="Bootstrap Script" subtitle="Automated token retrieval and secret creation">
        <p className="text-gray-600 dark:text-gray-400 mb-4">
          Run the bootstrap script to authenticate and create the Kubernetes Secret with all credentials:
        </p>
        <CodeBlock
          title="scripts/bootstrap-conduktor-token.sh"
          language="bash"
          code={bootstrapScript}
        />
        <div className="mt-4 p-4 bg-yellow-50 dark:bg-yellow-900/20 rounded-xl border border-yellow-200 dark:border-yellow-800">
          <p className="text-yellow-800 dark:text-yellow-200 text-sm">
            <strong>Usage:</strong> <code>./scripts/bootstrap-conduktor-token.sh</code><br />
            Override defaults with environment variables: <code>CONSOLE_URL</code>, <code>CONSOLE_USER</code>, <code>CONSOLE_PASSWORD</code>
          </p>
        </div>
      </Section>

      {/* Secret Structure */}
      <Section title="Kubernetes Secret" subtitle="Credential storage structure">
        <CardGrid columns={2}>
          <div className="col-span-2 lg:col-span-1">
            <h4 className="font-semibold text-gray-900 dark:text-gray-100 mb-4">Secret Keys</h4>
            <div className="space-y-3">
              {secretKeys.map((item) => (
                <div key={item.key} className="p-3 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
                  <code className="text-blue-500 font-mono text-sm">{item.key}</code>
                  <p className="text-gray-600 dark:text-gray-400 text-sm mt-1">{item.description}</p>
                  <p className="text-gray-500 dark:text-gray-500 text-xs mt-1 font-mono">e.g., {item.example}</p>
                </div>
              ))}
            </div>
          </div>
          <div className="col-span-2 lg:col-span-1">
            <CodeBlock
              title="k8s/conduktor-cli-secret.yaml"
              language="yaml"
              code={secretManifest}
            />
          </div>
        </CardGrid>
      </Section>

      {/* Java Implementation */}
      <Section title="Java Implementation" subtitle="How the operator loads and uses credentials">
        <div className="space-y-6">
          <div>
            <h4 className="font-semibold text-gray-900 dark:text-gray-100 mb-3">Credentials Loader</h4>
            <p className="text-gray-600 dark:text-gray-400 mb-4">
              The <code className="text-blue-500">ConduktorCliCredentials</code> class loads credentials from
              environment variables (priority) or mounted secret files:
            </p>
            <CodeBlock
              title="ConduktorCliCredentials.java"
              language="java"
              code={javaCredentials}
            />
          </div>

          <div>
            <h4 className="font-semibold text-gray-900 dark:text-gray-100 mb-3">CLI Executor</h4>
            <p className="text-gray-600 dark:text-gray-400 mb-4">
              The <code className="text-blue-500">ConduktorCli</code> class executes CLI commands with
              authentication via environment variables:
            </p>
            <CodeBlock
              title="ConduktorCli.java"
              language="java"
              code={cliExecution}
            />
          </div>
        </div>
      </Section>

      {/* Deployment Options */}
      <Section title="Deployment Options" subtitle="Two ways to inject credentials into the operator">
        <CardGrid columns={2}>
          <Card
            title="Environment Variables"
            description="Inject each secret key as an environment variable"
            icon={Key}
          >
            <CodeBlock
              title="deployment-env.yaml"
              language="yaml"
              code={deploymentEnvVars}
            />
          </Card>
          <Card
            title="Volume Mount"
            description="Mount the entire secret as files"
            icon={Database}
          >
            <CodeBlock
              title="deployment-volume.yaml"
              language="yaml"
              code={deploymentVolume}
            />
          </Card>
        </CardGrid>
      </Section>

      {/* Authentication Types */}
      <Section title="Authentication Types" subtitle="Console vs Gateway authentication">
        <CardGrid columns={2}>
          <Card
            title="Console API"
            description="Bearer Token Authentication"
            icon={Lock}
          >
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">Method:</span>
                <code className="text-blue-500">Authorization: Bearer &lt;token&gt;</code>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">Endpoint:</span>
                <code className="text-blue-500">POST /api/login</code>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">Response:</span>
                <code className="text-blue-500">{"{ access_token: \"...\" }"}</code>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">CLI Env:</span>
                <code className="text-blue-500">CDK_TOKEN</code>
              </div>
            </div>
          </Card>
          <Card
            title="Gateway Admin API"
            description="Basic Authentication"
            icon={Shield}
          >
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">Method:</span>
                <code className="text-blue-500">Basic Auth (user:pass)</code>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">Default User:</span>
                <code className="text-blue-500">admin</code>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">Default Pass:</span>
                <code className="text-blue-500">conduktor</code>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">CLI Env:</span>
                <code className="text-blue-500">CDK_GATEWAY_USER/PASSWORD</code>
              </div>
            </div>
          </Card>
        </CardGrid>
      </Section>

      {/* Quick Reference */}
      <Section title="Quick Reference" subtitle="Commands and environment variables">
        <CodeBlock
          title="Quick Start"
          language="bash"
          code={`# 1. Start Conduktor services (Console + Gateway)
docker compose up -d

# 2. Wait for services to be ready
curl -sf http://localhost:8080/platform/api/modules/resources/health/live

# 3. Run bootstrap script
./scripts/bootstrap-conduktor-token.sh

# 4. Verify secret was created
kubectl get secret conduktor-cli-credentials -o yaml

# 5. Test CLI manually (optional)
export CDK_BASE_URL=http://localhost:8080
export CDK_TOKEN=$(kubectl get secret conduktor-cli-credentials -o jsonpath='{.data.console-token}' | base64 -d)
conduktor apply -f my-resource.yaml --dry-run`}
        />
      </Section>
    </PageLayout>
  )
}
