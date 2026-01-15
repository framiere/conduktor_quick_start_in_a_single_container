import { TestTube2, CheckCircle2, XCircle, Code2, Server, Terminal, Layers } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import Section from '../components/Section'
import CodeBlock from '../components/CodeBlock'
import TestResult, { TestSuite, TestStats } from '../components/TestResult'

const testPyramid = [
  { level: 'E2E (Bats)', count: '~25', color: 'red', description: 'Full system tests in Minikube' },
  { level: 'Integration', count: '~15', color: 'orange', description: 'Component integration with Fabric8 mock' },
  { level: 'Unit', count: '~50', color: 'green', description: 'Fast, isolated class tests' },
]

const unitTests = [
  {
    name: 'OwnershipValidator validates same owner reference',
    status: 'passed',
    duration: '12ms',
    steps: [
      { keyword: 'Given', text: 'an ApplicationService "team-a" exists in the store' },
      { keyword: 'And', text: 'a ServiceAccount "sa-1" references "team-a"' },
      { keyword: 'When', text: 'I create a Topic referencing "sa-1" and "team-a"' },
      { keyword: 'Then', text: 'the validation should pass' }
    ]
  },
  {
    name: 'OwnershipValidator rejects cross-tenant reference',
    status: 'passed',
    duration: '8ms',
    steps: [
      { keyword: 'Given', text: 'ApplicationService "team-a" and "team-b" exist' },
      { keyword: 'And', text: 'ServiceAccount "sa-1" references "team-a"' },
      { keyword: 'When', text: 'I create a Topic referencing "sa-1" but owned by "team-b"' },
      { keyword: 'Then', text: 'the validation should fail with "different owner" message' }
    ]
  },
  {
    name: 'CRDStore publishes events on create',
    status: 'passed',
    duration: '15ms',
    steps: [
      { keyword: 'Given', text: 'a CRDStore with an event listener' },
      { keyword: 'When', text: 'I create a new Topic resource' },
      { keyword: 'Then', text: 'a BEFORE_CREATE event should be published' },
      { keyword: 'And', text: 'an AFTER_CREATE event should be published' }
    ]
  }
]

const integrationTests = [
  {
    name: 'Webhook accepts valid Topic creation',
    status: 'passed',
    duration: '245ms',
    description: 'Full admission review flow with Fabric8 mock server'
  },
  {
    name: 'Webhook rejects invalid ownership chain',
    status: 'passed',
    duration: '189ms',
    description: 'Verifies cross-tenant references are blocked'
  },
  {
    name: 'CRDStore persists resources correctly',
    status: 'passed',
    duration: '67ms',
    description: 'Tests thread-safe storage operations'
  }
]

const e2eTests = [
  {
    name: 'webhook has 2 ready replicas',
    status: 'passed',
    duration: '1.2s',
    description: 'HA deployment verification'
  },
  {
    name: 'webhook survives single pod failure',
    status: 'passed',
    duration: '8.5s',
    description: 'Failover test during pod deletion'
  },
  {
    name: 'rolling restart maintains availability',
    status: 'passed',
    duration: '45s',
    description: 'Zero-downtime deployment test'
  }
]

export default function TestingPage() {
  return (
    <PageLayout
      title="Testing"
      subtitle="Comprehensive test coverage across unit, integration, and E2E tests"
      icon={TestTube2}
      breadcrumb="Testing"
    >
      {/* Test Statistics */}
      <Section title="Test Overview" subtitle="Current test coverage statistics">
        <TestStats
          total={90}
          passed={87}
          failed={0}
          skipped={3}
          duration="2m 34s"
        />
      </Section>

      {/* Test Pyramid */}
      <Section title="Test Pyramid" subtitle="Different testing levels and their purpose">
        <div className="flex flex-col items-center py-8">
          {testPyramid.map((level, index) => {
            const width = 100 - (index * 25)
            return (
              <div
                key={level.level}
                className={`
                  flex items-center justify-between px-6 py-4 mb-2 rounded-xl
                  ${level.color === 'red' ? 'bg-red-100 dark:bg-red-900/30 border-red-200 dark:border-red-800' : ''}
                  ${level.color === 'orange' ? 'bg-orange-100 dark:bg-orange-900/30 border-orange-200 dark:border-orange-800' : ''}
                  ${level.color === 'green' ? 'bg-green-100 dark:bg-green-900/30 border-green-200 dark:border-green-800' : ''}
                  border-2
                `}
                style={{ width: `${width}%` }}
              >
                <div>
                  <div className="font-semibold">{level.level}</div>
                  <div className="text-sm text-gray-600 dark:text-gray-400">{level.description}</div>
                </div>
                <div className="text-2xl font-bold opacity-50">{level.count}</div>
              </div>
            )
          })}
        </div>
      </Section>

      {/* Unit Tests */}
      <Section title="Unit Tests" subtitle="Fast, isolated tests with Gherkin-style explanations">
        <div className="mb-4 flex items-center gap-4">
          <span className="text-sm text-gray-500">Framework:</span>
          <span className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-sm">JUnit 5</span>
          <span className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-sm">AssertJ</span>
        </div>
        <TestSuite
          name="OwnershipValidatorTest"
          tests={unitTests}
          stats={{ duration: '35ms' }}
        />

        <div className="mt-6">
          <h4 className="font-semibold mb-3">Example Test Code</h4>
          <CodeBlock
            title="OwnershipValidatorTest.java"
            language="java"
            code={`@Test
void shouldRejectTopicWithDifferentOwner() {
    // Given
    var appServiceA = TestDataBuilder.applicationService("team-a");
    var appServiceB = TestDataBuilder.applicationService("team-b");
    var serviceAccount = TestDataBuilder.serviceAccount("sa-1")
        .withApplicationServiceRef("team-a");

    store.create(appServiceA);
    store.create(appServiceB);
    store.create(serviceAccount);

    var topic = TestDataBuilder.topic("orders")
        .withServiceRef("sa-1")
        .withApplicationServiceRef("team-b");  // Different owner!

    // When
    var result = validator.validate(topic, Operation.CREATE);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getMessage())
        .contains("different applicationServiceRef");
}`}
          />
        </div>
      </Section>

      {/* Integration Tests */}
      <Section title="Integration Tests" subtitle="Component integration with Fabric8 mock Kubernetes">
        <div className="mb-4 flex items-center gap-4">
          <span className="text-sm text-gray-500">Framework:</span>
          <span className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-sm">JUnit 5</span>
          <span className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-sm">Fabric8 Mock Server</span>
        </div>
        <TestSuite
          name="WebhookIntegrationIT"
          tests={integrationTests}
          stats={{ duration: '501ms' }}
        />

        <div className="mt-6 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-xl border border-blue-200 dark:border-blue-800">
          <h4 className="font-semibold text-blue-800 dark:text-blue-200 mb-2">Test Base Classes</h4>
          <ul className="space-y-2 text-sm text-blue-700 dark:text-blue-300">
            <li><code className="bg-blue-100 dark:bg-blue-800 px-1 rounded">KubernetesITBase</code> - Sets up Fabric8 mock Kubernetes server</li>
            <li><code className="bg-blue-100 dark:bg-blue-800 px-1 rounded">ScenarioITBase</code> - Extends K8s base with scenario fixtures</li>
            <li><code className="bg-blue-100 dark:bg-blue-800 px-1 rounded">ComponentITBase</code> - Component/unit test setup</li>
          </ul>
        </div>
      </Section>

      {/* E2E Tests */}
      <Section title="E2E Tests (Bats)" subtitle="Full system tests running in Minikube">
        <div className="mb-4 flex items-center gap-4">
          <span className="text-sm text-gray-500">Framework:</span>
          <span className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-sm">Bats</span>
          <span className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-sm">Minikube</span>
          <span className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-sm">Helm</span>
        </div>
        <TestSuite
          name="HA Failover Tests"
          tests={e2eTests}
          stats={{ duration: '54.7s' }}
        />

        <div className="mt-6">
          <h4 className="font-semibold mb-3">Example Bats Test</h4>
          <CodeBlock
            title="05_ha_failover.bats"
            language="bash"
            code={`@test "webhook survives single pod failure" {
    # Get pod names
    local pods
    pods=$(get_webhook_pods)
    local first_pod
    first_pod=$(echo "$pods" | awk '{print $1}')

    echo "# Deleting pod: $first_pod" >&3

    # Delete the first pod (non-blocking)
    kubectl delete pod "$first_pod" -n "$NAMESPACE" --wait=false

    # Wait a moment for deletion to start
    sleep 2

    # Operations should still work (other pod handles requests)
    run create_topic "ha-topic-1" "ha-test-sa" "ha-test-app"
    assert_success

    # Wait for pod to be recreated
    wait_for 60 "[[ \\$(get_webhook_ready_replicas) -ge 2 ]]"
}`}
          />
        </div>
      </Section>

      {/* How to Run */}
      <Section title="Running Tests" subtitle="Commands for each test type">
        <CardGrid cols={3}>
          <Card icon={Code2} title="Unit Tests">
            <CodeBlock code="mvn test" className="mt-4" />
            <p className="text-sm text-gray-500 mt-2">Runs *Test.java files</p>
          </Card>
          <Card icon={Layers} title="Integration Tests">
            <CodeBlock code="mvn verify" className="mt-4" />
            <p className="text-sm text-gray-500 mt-2">Runs *IT.java files</p>
          </Card>
          <Card icon={Server} title="E2E Tests">
            <CodeBlock code="./functional-tests/run-tests.sh" className="mt-4" />
            <p className="text-sm text-gray-500 mt-2">Full Minikube deployment</p>
          </Card>
        </CardGrid>
      </Section>
    </PageLayout>
  )
}
