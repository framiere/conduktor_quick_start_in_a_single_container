import { Code2, Terminal, GitBranch, Package, Wrench, CheckCircle2, AlertCircle, FileCode } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import Section from '../components/Section'
import CodeBlock from '../components/CodeBlock'

const buildSteps = [
  { step: 1, title: 'Clone Repository', command: 'git clone <repo-url>', description: 'Get the source code' },
  { step: 2, title: 'Install Dependencies', command: 'mvn dependency:resolve', description: 'Download all Maven dependencies' },
  { step: 3, title: 'Compile', command: 'mvn compile', description: 'Compile the Java sources' },
  { step: 4, title: 'Run Tests', command: 'mvn test', description: 'Execute unit tests' },
  { step: 5, title: 'Package', command: 'mvn package -DskipTests', description: 'Create executable JAR' },
  { step: 6, title: 'Format Check', command: 'mvn spotless:check', description: 'Verify code formatting' },
]

const projectStructure = `
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/messaging/operator/
│   │   │       ├── crd/           # CRD classes
│   │   │       ├── events/        # Event publishing
│   │   │       ├── store/         # In-memory storage
│   │   │       ├── validation/    # Ownership validation
│   │   │       └── webhook/       # HTTP server
│   │   └── resources/
│   │       └── crds-deployment.yaml
│   └── test/
│       ├── java/                  # Test classes
│       └── resources/fixtures/    # Test fixtures
├── functional-tests/
│   ├── bats/                      # Bats E2E tests
│   ├── helm/                      # Helm chart
│   ├── setup-minikube.sh          # Cluster setup
│   ├── deploy.sh                  # Helm deployment
│   └── run-tests.sh               # Test runner
├── k8s/                           # K8s manifests
└── pom.xml                        # Maven config
`

const codingStandards = [
  { title: 'AssertJ for Assertions', description: 'Use AssertJ fluent assertions, never JUnit assertEquals', good: 'assertThat(result).isEqualTo(expected)', bad: 'assertEquals(expected, result)' },
  { title: 'Builder Pattern', description: 'Use builders for 3+ parameters', good: 'Topic.builder().name("x").partitions(3).build()', bad: 'new Topic("x", null, 3, 1, null)' },
  { title: 'Enum over Strings', description: 'Use CRDKind enum instead of string literals', good: 'CRDKind.TOPIC', bad: '"Topic"' },
  { title: 'Text Blocks for SQL/YAML', description: 'Use Java text blocks for multi-line strings', good: '"""\nSELECT * FROM..."""', bad: '"SELECT * FROM " + ...' },
]

export default function DeveloperPage() {
  return (
    <PageLayout
      title="Developer Guide"
      subtitle="Build, compile, test, and contribute to the project"
      icon={Code2}
      breadcrumb="Developer"
      aphorism={{
        text: "Simplicity is the ultimate sophistication.",
        author: "Leonardo da Vinci"
      }}
    >
      {/* Quick Start */}
      <Section title="Quick Start" subtitle="Get up and running in 5 minutes">
        <CodeBlock
          title="Terminal"
          code={`# Clone and build
git clone <repository-url>
cd messaging-operator

# Build with Maven
mvn clean package -DskipTests

# Run unit tests
mvn test

# Run integration tests
mvn verify

# Format code
mvn spotless:apply`}
        />
      </Section>

      {/* Build Pipeline */}
      <Section title="Build Pipeline" subtitle="Step-by-step build process">
        <div className="space-y-4">
          {buildSteps.map((item) => (
            <div key={item.step} className="flex items-center gap-4 p-4 bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800">
              <div className="w-10 h-10 bg-blue-500 text-white rounded-full flex items-center justify-center font-bold shrink-0">
                {item.step}
              </div>
              <div className="flex-1">
                <div className="font-semibold">{item.title}</div>
                <div className="text-sm text-gray-500">{item.description}</div>
              </div>
              <code className="px-3 py-1.5 bg-gray-100 dark:bg-gray-800 rounded text-sm font-mono">
                {item.command}
              </code>
            </div>
          ))}
        </div>
      </Section>

      {/* Project Structure */}
      <Section title="Project Structure" subtitle="Repository layout and key directories">
        <CodeBlock
          title="Directory Structure"
          language="text"
          code={projectStructure}
        />
      </Section>

      {/* Technology Stack */}
      <Section title="Technology Stack">
        <CardGrid cols={3}>
          <Card title="Java 21">
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Latest LTS with text blocks, pattern matching, and records
            </p>
          </Card>
          <Card title="Maven">
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Build tool with shade plugin for uber-JAR packaging
            </p>
          </Card>
          <Card title="Fabric8 Client">
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Kubernetes Java client with CRD support
            </p>
          </Card>
          <Card title="JUnit 5">
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Test framework with parameterized tests
            </p>
          </Card>
          <Card title="AssertJ">
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Fluent assertion library (required)
            </p>
          </Card>
          <Card title="Lombok">
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Reduce boilerplate with @Builder, @Getter
            </p>
          </Card>
        </CardGrid>
      </Section>

      {/* Coding Standards */}
      <Section title="Coding Standards" subtitle="Required patterns and conventions">
        <div className="space-y-6">
          {codingStandards.map((standard, index) => (
            <Card key={index}>
              <h3 className="font-semibold mb-2">{standard.title}</h3>
              <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">{standard.description}</p>
              <div className="grid md:grid-cols-2 gap-4">
                <div>
                  <div className="flex items-center gap-2 text-green-600 dark:text-green-400 text-sm font-medium mb-2">
                    <CheckCircle2 size={16} />
                    Good
                  </div>
                  <code className="block p-3 bg-green-50 dark:bg-green-900/20 rounded-lg text-sm">
                    {standard.good}
                  </code>
                </div>
                <div>
                  <div className="flex items-center gap-2 text-red-600 dark:text-red-400 text-sm font-medium mb-2">
                    <AlertCircle size={16} />
                    Avoid
                  </div>
                  <code className="block p-3 bg-red-50 dark:bg-red-900/20 rounded-lg text-sm">
                    {standard.bad}
                  </code>
                </div>
              </div>
            </Card>
          ))}
        </div>
      </Section>

      {/* Git Workflow */}
      <Section title="Git Workflow" subtitle="Commit message conventions">
        <Card>
          <h3 className="font-semibold mb-4">Conventional Commits</h3>
          <div className="space-y-3">
            {[
              { type: 'feat', desc: 'New feature', example: 'feat(crd): add ConsumerGroup resource' },
              { type: 'fix', desc: 'Bug fix', example: 'fix(webhook): handle null serviceRef' },
              { type: 'docs', desc: 'Documentation', example: 'docs: update README with quick start' },
              { type: 'refactor', desc: 'Code refactoring', example: 'refactor(store): extract event publisher' },
              { type: 'test', desc: 'Tests', example: 'test(validation): add cross-tenant tests' },
              { type: 'chore', desc: 'Maintenance', example: 'chore: update dependencies' },
            ].map((item) => (
              <div key={item.type} className="flex items-center gap-4">
                <code className="px-2 py-1 bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded text-sm w-24">
                  {item.type}
                </code>
                <span className="text-gray-500 w-32">{item.desc}</span>
                <code className="text-sm text-gray-600 dark:text-gray-400">{item.example}</code>
              </div>
            ))}
          </div>
        </Card>

        <div className="mt-6">
          <h4 className="font-semibold mb-3">Commit Message Rules</h4>
          <ul className="space-y-2 text-sm">
            {[
              'Limit subject line to 50 characters',
              'Capitalize the subject line',
              'Do not end subject with a period',
              'Use imperative mood ("Add feature" not "Added feature")',
              'Wrap body at 120 characters',
              'Use body to explain what and why, not how',
            ].map((rule, index) => (
              <li key={index} className="flex items-center gap-2 text-gray-600 dark:text-gray-400">
                <CheckCircle2 size={14} className="text-green-500" />
                {rule}
              </li>
            ))}
          </ul>
        </div>
      </Section>

      {/* IDE Setup */}
      <Section title="IDE Setup">
        <CardGrid cols={2}>
          <Card icon={FileCode} title="IntelliJ IDEA">
            <ul className="space-y-2 text-sm text-gray-600 dark:text-gray-400 mt-4">
              <li>• Import as Maven project</li>
              <li>• Enable Lombok plugin</li>
              <li>• Set Java 21 SDK</li>
              <li>• Import Eclipse formatter settings</li>
            </ul>
          </Card>
          <Card icon={Terminal} title="VS Code">
            <ul className="space-y-2 text-sm text-gray-600 dark:text-gray-400 mt-4">
              <li>• Install Java Extension Pack</li>
              <li>• Install Lombok Annotations Support</li>
              <li>• Configure java.home to Java 21</li>
            </ul>
          </Card>
        </CardGrid>
      </Section>
    </PageLayout>
  )
}
