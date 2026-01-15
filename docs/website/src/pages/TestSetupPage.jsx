import {
  Wrench, CheckCircle2, AlertCircle, Terminal, Box, Ship,
  Container, Layers, HelpCircle, ChevronRight, ExternalLink,
  Cpu, HardDrive, Clock, BookOpen
} from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import Section from '../components/Section'
import CodeBlock from '../components/CodeBlock'

// Tool descriptions for people unfamiliar with them
const tools = [
  {
    id: 'docker',
    name: 'Docker',
    icon: Container,
    color: '#2496ED',
    tagline: 'Container runtime',
    description: 'Docker lets you run applications in isolated containers. Think of it as lightweight virtual machines that share the host OS. Minikube uses Docker to run Kubernetes nodes.',
    why: 'Required by Minikube to create the Kubernetes cluster',
    website: 'https://docs.docker.com/get-docker/',
    version: '20.10+',
  },
  {
    id: 'minikube',
    name: 'Minikube',
    icon: Box,
    color: '#326CE5',
    tagline: 'Local Kubernetes cluster',
    description: 'Minikube runs a single-node Kubernetes cluster on your laptop. It\'s perfect for development and testing - you get a real Kubernetes environment without needing cloud infrastructure.',
    why: 'Creates the Kubernetes cluster where we deploy and test the webhook',
    website: 'https://minikube.sigs.k8s.io/docs/start/',
    version: '1.32+',
  },
  {
    id: 'kubectl',
    name: 'kubectl',
    icon: Terminal,
    color: '#326CE5',
    tagline: 'Kubernetes CLI',
    description: 'kubectl (pronounced "kube-control" or "kube-cuddle") is the command-line tool to interact with Kubernetes. You use it to deploy apps, inspect resources, and view logs.',
    why: 'Used by test scripts to create resources and verify webhook behavior',
    website: 'https://kubernetes.io/docs/tasks/tools/',
    version: '1.29+',
  },
  {
    id: 'helm',
    name: 'Helm',
    icon: Ship,
    color: '#0F1689',
    tagline: 'Kubernetes package manager',
    description: 'Helm is like apt or brew for Kubernetes. It packages multiple Kubernetes resources (deployments, services, configs) into a single "chart" that can be installed with one command.',
    why: 'Deploys the webhook and all its dependencies with proper configuration',
    website: 'https://helm.sh/docs/intro/install/',
    version: '3.14+',
  },
  {
    id: 'bats',
    name: 'Bats',
    icon: Layers,
    color: '#4EAA25',
    tagline: 'Bash testing framework',
    description: 'Bats (Bash Automated Testing System) lets you write tests in Bash. Each test is a function that runs commands and checks results. It\'s perfect for testing CLI tools and infrastructure.',
    why: 'Runs our E2E test suite that validates the webhook in a real cluster',
    website: 'https://bats-core.readthedocs.io/',
    version: '1.10+',
  },
]

// System requirements
const systemRequirements = [
  { icon: Cpu, label: 'CPU', value: '2+ cores available', note: 'Minikube needs dedicated CPU resources' },
  { icon: HardDrive, label: 'RAM', value: '4GB+ available', note: '4GB for Minikube + your apps' },
  { icon: HardDrive, label: 'Disk', value: '20GB+ free', note: 'Docker images and Minikube VM' },
  { icon: Clock, label: 'Time', value: '~15 minutes', note: 'First-time setup with downloads' },
]

// OS-specific installation commands
const installCommands = {
  ubuntu: {
    name: 'Ubuntu / Debian',
    docker: `# Add Docker's official GPG key and repository
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Add repository
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io

# Add your user to docker group (logout/login required)
sudo usermod -aG docker $USER`,
    minikube: `# Download and install Minikube
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube_latest_amd64.deb
sudo dpkg -i minikube_latest_amd64.deb
rm minikube_latest_amd64.deb`,
    kubectl: `# Add Kubernetes apt repository
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.29/deb/Release.key | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.29/deb/ /' | sudo tee /etc/apt/sources.list.d/kubernetes.list

# Install kubectl
sudo apt-get update
sudo apt-get install -y kubectl`,
    helm: `# Use official install script
curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash`,
    bats: `# Install Bats and helper libraries
sudo apt-get update
sudo apt-get install -y bats

# Install bats-support and bats-assert
sudo mkdir -p /usr/lib/bats-support /usr/lib/bats-assert
sudo git clone https://github.com/bats-core/bats-support.git /usr/lib/bats-support
sudo git clone https://github.com/bats-core/bats-assert.git /usr/lib/bats-assert`,
  },
  macos: {
    name: 'macOS',
    docker: `# Install Docker Desktop from:
# https://docs.docker.com/desktop/install/mac-install/

# Or use Homebrew (installs CLI only, not Desktop)
brew install docker`,
    minikube: `brew install minikube`,
    kubectl: `brew install kubectl`,
    helm: `brew install helm`,
    bats: `brew install bats-core bats-support bats-assert`,
  },
}

// Common issues and solutions
const troubleshooting = [
  {
    problem: 'Docker permission denied',
    symptom: 'Got permission denied while trying to connect to the Docker daemon socket',
    solution: 'Add your user to the docker group and log out/in:\nsudo usermod -aG docker $USER\nThen log out and back in.',
  },
  {
    problem: 'Minikube won\'t start',
    symptom: 'minikube start fails with "Exiting due to DRV_NOT_DETECTED"',
    solution: 'Make sure Docker is running:\nsudo systemctl start docker\nOr open Docker Desktop on macOS.',
  },
  {
    problem: 'Not enough memory',
    symptom: 'Minikube fails with memory allocation errors',
    solution: 'Reduce Minikube memory (minimum 2GB):\nminikube start --memory=2048',
  },
  {
    problem: 'kubectl can\'t connect',
    symptom: 'The connection to the server localhost:8080 was refused',
    solution: 'Point kubectl to Minikube:\nminikube kubectl -- get pods\nOr: eval $(minikube docker-env)',
  },
  {
    problem: 'Bats libraries not found',
    symptom: 'Error: bats-support/load.bash not found',
    solution: 'Install bats helper libraries or set BATS_LIB_PATH:\nexport BATS_LIB_PATH=/usr/lib',
  },
]

function ToolCard({ tool }) {
  const Icon = tool.icon
  return (
    <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 p-6 hover:shadow-lg transition-shadow">
      <div className="flex items-start gap-4">
        <div
          className="w-12 h-12 rounded-xl flex items-center justify-center shrink-0"
          style={{ backgroundColor: `${tool.color}20` }}
        >
          <Icon size={24} style={{ color: tool.color }} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="font-semibold text-lg">{tool.name}</h3>
            <span className="text-xs px-2 py-0.5 bg-gray-100 dark:bg-gray-800 rounded-full text-gray-500">
              {tool.version}
            </span>
          </div>
          <div className="text-sm text-gray-500 dark:text-gray-400 mb-2">{tool.tagline}</div>
          <p className="text-sm text-gray-600 dark:text-gray-300 mb-3">{tool.description}</p>
          <div className="flex items-start gap-2 text-sm">
            <HelpCircle size={16} className="text-blue-500 shrink-0 mt-0.5" />
            <span className="text-blue-600 dark:text-blue-400">{tool.why}</span>
          </div>
        </div>
      </div>
      <a
        href={tool.website}
        target="_blank"
        rel="noopener noreferrer"
        className="mt-4 flex items-center gap-2 text-sm text-gray-500 hover:text-blue-500 transition-colors"
      >
        <ExternalLink size={14} />
        Official documentation
      </a>
    </div>
  )
}

function StepNumber({ number }) {
  return (
    <div className="w-8 h-8 bg-blue-500 text-white rounded-full flex items-center justify-center font-bold text-sm shrink-0">
      {number}
    </div>
  )
}

function TroubleshootingCard({ issue }) {
  return (
    <div className="bg-amber-50 dark:bg-amber-900/20 rounded-xl border border-amber-200 dark:border-amber-800 p-4">
      <div className="flex items-start gap-3">
        <AlertCircle size={20} className="text-amber-500 shrink-0 mt-0.5" />
        <div>
          <h4 className="font-semibold text-amber-800 dark:text-amber-200">{issue.problem}</h4>
          <p className="text-sm text-amber-700 dark:text-amber-300 mt-1">{issue.symptom}</p>
          <div className="mt-3 p-3 bg-white dark:bg-gray-900 rounded-lg">
            <pre className="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap font-mono">
              {issue.solution}
            </pre>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function TestSetupPage() {
  return (
    <PageLayout
      title="Test Environment Setup"
      subtitle="Everything you need to run E2E tests locally"
      icon={Wrench}
      breadcrumb="Test Setup"
      aphorism={{
        text: "Give me six hours to chop down a tree and I will spend the first four sharpening the axe.",
        author: "Abraham Lincoln"
      }}
    >
      {/* Introduction */}
      <Section title="What You'll Set Up" subtitle="A complete local Kubernetes testing environment">
        <div className="bg-gradient-to-r from-blue-50 to-purple-50 dark:from-blue-900/20 dark:to-purple-900/20 rounded-2xl p-6 mb-8 border border-blue-100 dark:border-blue-800">
          <div className="flex items-start gap-4">
            <BookOpen size={24} className="text-blue-500 shrink-0 mt-1" />
            <div>
              <h3 className="font-semibold text-lg mb-2">New to Kubernetes?</h3>
              <p className="text-gray-600 dark:text-gray-300 mb-3">
                Don't worry! This guide explains each tool and walks you through step-by-step.
                By the end, you'll have a real Kubernetes cluster running on your laptop where
                you can deploy and test the messaging operator.
              </p>
              <p className="text-sm text-gray-500">
                <strong>The big picture:</strong> We create a mini Kubernetes cluster (Minikube),
                deploy our webhook there (Helm), and run tests that verify it works correctly (Bats).
              </p>
            </div>
          </div>
        </div>

        {/* System Requirements */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
          {systemRequirements.map((req) => {
            const Icon = req.icon
            return (
              <div key={req.label} className="bg-white dark:bg-gray-900 rounded-xl p-4 border border-gray-200 dark:border-gray-800 text-center">
                <Icon size={24} className="mx-auto text-gray-400 mb-2" />
                <div className="font-semibold">{req.value}</div>
                <div className="text-xs text-gray-500">{req.label}</div>
                <div className="text-xs text-gray-400 mt-1">{req.note}</div>
              </div>
            )
          })}
        </div>
      </Section>

      {/* Tools Explanation */}
      <Section title="Tools We'll Install" subtitle="Understanding what each tool does">
        <div className="grid md:grid-cols-2 gap-6">
          {tools.map((tool) => (
            <ToolCard key={tool.id} tool={tool} />
          ))}
        </div>
      </Section>

      {/* Quick Install */}
      <Section title="Quick Install" subtitle="One command to install everything">
        <div className="bg-green-50 dark:bg-green-900/20 rounded-xl p-6 border border-green-200 dark:border-green-800 mb-6">
          <div className="flex items-start gap-4">
            <CheckCircle2 size={24} className="text-green-500 shrink-0" />
            <div>
              <h3 className="font-semibold text-green-800 dark:text-green-200 mb-2">Automated Installation</h3>
              <p className="text-sm text-green-700 dark:text-green-300 mb-4">
                We provide a script that detects your OS and installs all required tools.
                It's safe to run multiple times - it skips already-installed tools.
              </p>
              <CodeBlock
                code={`# Check what's already installed
./functional-tests/install-requirements.sh --check

# Install all missing tools
./functional-tests/install-requirements.sh

# Force reinstall everything
./functional-tests/install-requirements.sh --force`}
              />
            </div>
          </div>
        </div>

        <div className="text-center text-gray-500 my-6">
          <div className="flex items-center justify-center gap-4">
            <div className="h-px bg-gray-300 dark:bg-gray-700 w-24"></div>
            <span>or install manually below</span>
            <div className="h-px bg-gray-300 dark:bg-gray-700 w-24"></div>
          </div>
        </div>
      </Section>

      {/* Manual Installation - Ubuntu */}
      <Section title="Manual Installation (Ubuntu/Debian)" subtitle="Step-by-step commands for Ubuntu and Debian">
        <div className="space-y-6">
          <div className="flex items-start gap-4">
            <StepNumber number={1} />
            <div className="flex-1">
              <h3 className="font-semibold mb-2">Install Docker</h3>
              <p className="text-sm text-gray-500 mb-3">Docker is the foundation - Minikube runs Kubernetes inside Docker containers.</p>
              <CodeBlock code={installCommands.ubuntu.docker} />
              <div className="mt-3 p-3 bg-amber-50 dark:bg-amber-900/20 rounded-lg text-sm text-amber-700 dark:text-amber-300">
                <strong>Important:</strong> After adding yourself to the docker group, you must log out and back in for it to take effect.
              </div>
            </div>
          </div>

          <div className="flex items-start gap-4">
            <StepNumber number={2} />
            <div className="flex-1">
              <h3 className="font-semibold mb-2">Install Minikube</h3>
              <p className="text-sm text-gray-500 mb-3">Minikube creates a single-node Kubernetes cluster on your machine.</p>
              <CodeBlock code={installCommands.ubuntu.minikube} />
            </div>
          </div>

          <div className="flex items-start gap-4">
            <StepNumber number={3} />
            <div className="flex-1">
              <h3 className="font-semibold mb-2">Install kubectl</h3>
              <p className="text-sm text-gray-500 mb-3">The Kubernetes command-line tool to manage your cluster.</p>
              <CodeBlock code={installCommands.ubuntu.kubectl} />
            </div>
          </div>

          <div className="flex items-start gap-4">
            <StepNumber number={4} />
            <div className="flex-1">
              <h3 className="font-semibold mb-2">Install Helm</h3>
              <p className="text-sm text-gray-500 mb-3">The package manager we use to deploy the webhook.</p>
              <CodeBlock code={installCommands.ubuntu.helm} />
            </div>
          </div>

          <div className="flex items-start gap-4">
            <StepNumber number={5} />
            <div className="flex-1">
              <h3 className="font-semibold mb-2">Install Bats</h3>
              <p className="text-sm text-gray-500 mb-3">The test framework and its helper libraries.</p>
              <CodeBlock code={installCommands.ubuntu.bats} />
            </div>
          </div>
        </div>
      </Section>

      {/* Manual Installation - macOS */}
      <Section title="Manual Installation (macOS)" subtitle="Using Homebrew for macOS users">
        <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-6 border border-gray-200 dark:border-gray-800">
          <p className="text-sm text-gray-600 dark:text-gray-300 mb-4">
            On macOS, we recommend using <a href="https://brew.sh" className="text-blue-500 hover:underline" target="_blank" rel="noopener noreferrer">Homebrew</a>.
            For Docker, install <a href="https://docs.docker.com/desktop/install/mac-install/" className="text-blue-500 hover:underline" target="_blank" rel="noopener noreferrer">Docker Desktop</a> first.
          </p>
          <CodeBlock
            code={`# Install all tools with Homebrew
brew install minikube kubectl helm bats-core bats-support bats-assert

# Don't forget Docker Desktop!
# Download from: https://docs.docker.com/desktop/install/mac-install/`}
          />
        </div>
      </Section>

      {/* Verify Installation */}
      <Section title="Verify Installation" subtitle="Make sure everything is working">
        <CodeBlock
          title="Run these commands to verify"
          code={`# Check all tool versions
docker --version          # Docker version 24.x or higher
minikube version          # minikube version: v1.32.x
kubectl version --client  # Client Version: v1.29.x
helm version --short      # v3.14.x
bats --version            # Bats 1.10.x

# Or use our check script
./functional-tests/install-requirements.sh --check`}
        />

        <div className="mt-6 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-xl border border-blue-200 dark:border-blue-800">
          <h4 className="font-semibold text-blue-800 dark:text-blue-200 mb-2">Expected Output</h4>
          <pre className="text-xs text-blue-700 dark:text-blue-300 font-mono whitespace-pre">
{`========================================
  Requirements Status
========================================

TOOL            STATUS       VERSION
----            ------       -------
docker          INSTALLED    24.0.7
minikube        INSTALLED    v1.32.0
kubectl         INSTALLED    v1.29.0
helm            INSTALLED    v3.14.0
bats            INSTALLED    Bats 1.10.0

LIBRARY         STATUS       PATH
-------         ------       ----
bats-support    INSTALLED    /usr/lib/bats-support
bats-assert     INSTALLED    /usr/lib/bats-assert

[OK] All requirements are installed!`}
          </pre>
        </div>
      </Section>

      {/* Run Your First Test */}
      <Section title="Run Your First Test" subtitle="Let's make sure everything works together">
        <div className="space-y-4">
          <div className="flex items-start gap-4">
            <StepNumber number={1} />
            <div className="flex-1">
              <h3 className="font-semibold mb-2">Start Minikube</h3>
              <CodeBlock code={`# Start a cluster (takes 1-2 minutes first time)
minikube start --cpus=2 --memory=4096

# Verify it's running
kubectl get nodes`} />
            </div>
          </div>

          <div className="flex items-start gap-4">
            <StepNumber number={2} />
            <div className="flex-1">
              <h3 className="font-semibold mb-2">Run the Full Test Suite</h3>
              <CodeBlock code={`# This will:
# 1. Build the webhook Docker image
# 2. Deploy it to Minikube with Helm
# 3. Run all Bats E2E tests
# 4. Clean up when done

./functional-tests/run-tests.sh`} />
            </div>
          </div>

          <div className="flex items-start gap-4">
            <StepNumber number={3} />
            <div className="flex-1">
              <h3 className="font-semibold mb-2">View Results</h3>
              <p className="text-sm text-gray-500 mb-3">
                You should see green checkmarks for passing tests. If something fails,
                check the troubleshooting section below.
              </p>
            </div>
          </div>
        </div>
      </Section>

      {/* Troubleshooting */}
      <Section title="Troubleshooting" subtitle="Common issues and how to fix them">
        <div className="space-y-4">
          {troubleshooting.map((issue, idx) => (
            <TroubleshootingCard key={idx} issue={issue} />
          ))}
        </div>
      </Section>

      {/* Cleanup */}
      <Section title="Cleanup" subtitle="Remove everything when you're done">
        <CodeBlock
          code={`# Stop Minikube (keeps data for next time)
minikube stop

# Delete the cluster completely
minikube delete

# Remove test namespace only
kubectl delete namespace test-*`}
        />
        <p className="text-sm text-gray-500 mt-4">
          Tip: Keep Minikube running between test sessions to save startup time.
          Use <code className="bg-gray-100 dark:bg-gray-800 px-1 rounded">minikube stop</code> instead of delete.
        </p>
      </Section>

      {/* Next Steps */}
      <Section title="Next Steps" subtitle="Ready to explore more?">
        <CardGrid cols={3}>
          <Card icon={Terminal} title="Run Tests" href="#/testing">
            Learn about the different test types and how to run them
          </Card>
          <Card icon={Ship} title="Helm Chart" href="#/operations">
            Understand the Helm chart configuration options
          </Card>
          <Card icon={Box} title="CRDs" href="#/crds">
            Explore the Custom Resource Definitions we're testing
          </Card>
        </CardGrid>
      </Section>
    </PageLayout>
  )
}
