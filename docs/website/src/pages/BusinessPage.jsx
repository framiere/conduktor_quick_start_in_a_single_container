import { Users, DollarSign, Clock, Shield, Zap, CheckCircle2 } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import Card, { CardGrid } from '../components/Card'
import Section from '../components/Section'

const valueProps = [
  {
    icon: Shield,
    title: 'Multi-Tenant Isolation',
    description: 'Each team operates in their own secure sandbox. Resources are strictly isolated through ownership chains.',
    benefit: 'Zero risk of cross-team interference'
  },
  {
    icon: DollarSign,
    title: 'Cost Efficiency',
    description: 'Share a single messaging cluster across multiple teams while maintaining strict isolation.',
    benefit: 'Up to 70% infrastructure cost reduction'
  },
  {
    icon: Clock,
    title: 'Self-Service Platform',
    description: 'Teams can provision their own topics, ACLs, and consumer groups through kubectl or GitOps.',
    benefit: 'Minutes instead of days for resource provisioning'
  },
  {
    icon: Zap,
    title: 'GitOps Native',
    description: 'Declarative CRDs work seamlessly with ArgoCD, Flux, and any GitOps workflow.',
    benefit: 'Full audit trail and version control'
  }
]

const personas = [
  {
    role: 'Platform Engineer',
    needs: 'Manage messaging infrastructure at scale with minimal overhead',
    value: 'Deploy once, configure through CRDs, monitor through standard K8s tooling'
  },
  {
    role: 'Application Developer',
    needs: 'Quickly provision topics and permissions for their services',
    value: 'Self-service through kubectl, no tickets, immediate feedback on invalid configs'
  },
  {
    role: 'Security/Compliance',
    needs: 'Ensure proper access controls and audit trails',
    value: 'Ownership chains prevent cross-tenant access, all changes tracked in Git'
  },
  {
    role: 'FinOps',
    needs: 'Optimize infrastructure costs while meeting team requirements',
    value: 'Multi-tenancy on shared clusters, resource tracking per ApplicationService'
  }
]

export default function BusinessPage() {
  return (
    <PageLayout
      title="Business Value"
      subtitle="Understanding the operator from a business and stakeholder perspective"
      icon={Users}
      breadcrumb="Business"
      aphorism={{
        text: "Price is what you pay. Value is what you get.",
        author: "Warren Buffett"
      }}
    >
      {/* Value Proposition */}
      <Section title="Value Proposition" subtitle="Why organizations choose this operator">
        <CardGrid cols={2}>
          {valueProps.map((prop, index) => {
            const Icon = prop.icon
            return (
              <Card key={index}>
                <div className="flex items-start gap-4">
                  <div className="p-3 bg-green-100 dark:bg-green-900/30 rounded-xl shrink-0">
                    <Icon className="text-green-600 dark:text-green-400" size={24} />
                  </div>
                  <div>
                    <h3 className="font-semibold mb-2">{prop.title}</h3>
                    <p className="text-gray-600 dark:text-gray-400 text-sm mb-3">{prop.description}</p>
                    <div className="flex items-center gap-2 text-green-600 dark:text-green-400 text-sm font-medium">
                      <CheckCircle2 size={16} />
                      {prop.benefit}
                    </div>
                  </div>
                </div>
              </Card>
            )
          })}
        </CardGrid>
      </Section>

      {/* ROI Calculator Visual */}
      <Section title="Return on Investment" subtitle="Typical benefits observed">
        <div className="flex justify-center">
          <div className="bg-gradient-to-br from-purple-50 to-pink-50 dark:from-purple-900/20 dark:to-pink-900/20 rounded-2xl p-8 border border-purple-200 dark:border-purple-800 text-center max-w-md">
            <div className="text-5xl font-bold text-purple-600 dark:text-purple-400 mb-2">100%</div>
            <div className="text-lg text-purple-700 dark:text-purple-300">Audit Compliance</div>
            <p className="text-sm text-gray-500 mt-3">Full GitOps trail for all changes</p>
          </div>
        </div>
      </Section>

      {/* Stakeholder Personas */}
      <Section title="Stakeholder Personas" subtitle="Value delivered to different roles">
        <div className="grid md:grid-cols-2 gap-6">
          {personas.map((persona, index) => (
            <Card key={index}>
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 bg-gradient-to-br from-blue-500 to-purple-500 rounded-full flex items-center justify-center text-white font-bold">
                  {persona.role.charAt(0)}
                </div>
                <h3 className="font-semibold">{persona.role}</h3>
              </div>
              <div className="space-y-3">
                <div>
                  <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Needs</span>
                  <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{persona.needs}</p>
                </div>
                <div>
                  <span className="text-xs font-semibold text-green-600 dark:text-green-400 uppercase tracking-wider">Value Delivered</span>
                  <p className="text-sm text-gray-700 dark:text-gray-300 mt-1">{persona.value}</p>
                </div>
              </div>
            </Card>
          ))}
        </div>
      </Section>

      {/* Comparison Table */}
      <Section title="Before vs After" subtitle="What changes with the operator">
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 dark:bg-gray-800">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold">Aspect</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-red-600 dark:text-red-400">Before (Manual)</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-green-600 dark:text-green-400">After (Operator)</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
              <tr>
                <td className="px-6 py-4 font-medium">Resource Provisioning</td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Tickets, days of wait time</td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Self-service, instant</td>
              </tr>
              <tr>
                <td className="px-6 py-4 font-medium">Access Control</td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Manual ACL configuration</td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Declarative, validated</td>
              </tr>
              <tr>
                <td className="px-6 py-4 font-medium">Multi-Tenancy</td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Separate clusters per team</td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Shared cluster, isolated namespaces</td>
              </tr>
              <tr>
                <td className="px-6 py-4 font-medium">Audit Trail</td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Scattered logs, manual tracking</td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Git history, event stream</td>
              </tr>
              <tr>
                <td className="px-6 py-4 font-medium">Configuration Drift</td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Common, hard to detect</td>
                <td className="px-6 py-4 text-gray-600 dark:text-gray-400">Impossible (reconciliation)</td>
              </tr>
            </tbody>
          </table>
        </div>
      </Section>
    </PageLayout>
  )
}
