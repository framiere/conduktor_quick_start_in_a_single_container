import { Users, DollarSign, Clock, Shield, Zap, TrendingUp, CheckCircle2, Building2, Target, Layers } from 'lucide-react'
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

const useCases = [
  {
    title: 'Enterprise Messaging Platform',
    description: 'Large organizations with multiple business units sharing a central Kafka infrastructure.',
    teams: ['Platform Engineering', 'Data Engineering', 'Application Teams'],
    benefits: ['Centralized management', 'Consistent policies', 'Resource optimization']
  },
  {
    title: 'SaaS Multi-Tenancy',
    description: 'SaaS providers offering isolated messaging capabilities to each customer.',
    teams: ['Customer A', 'Customer B', 'Customer C'],
    benefits: ['Customer isolation', 'Usage tracking', 'Custom quotas']
  },
  {
    title: 'Development Environments',
    description: 'Providing isolated messaging environments for development, staging, and production.',
    teams: ['Dev Team', 'QA Team', 'Production'],
    benefits: ['Environment parity', 'Safe testing', 'Promotion workflows']
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
        <div className="grid md:grid-cols-3 gap-6">
          <div className="bg-gradient-to-br from-green-50 to-emerald-50 dark:from-green-900/20 dark:to-emerald-900/20 rounded-2xl p-6 border border-green-200 dark:border-green-800 text-center">
            <div className="text-4xl font-bold text-green-600 dark:text-green-400 mb-2">70%</div>
            <div className="text-sm text-green-700 dark:text-green-300">Infrastructure Cost Reduction</div>
            <p className="text-xs text-gray-500 mt-2">Through multi-tenant resource sharing</p>
          </div>
          <div className="bg-gradient-to-br from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 rounded-2xl p-6 border border-blue-200 dark:border-blue-800 text-center">
            <div className="text-4xl font-bold text-blue-600 dark:text-blue-400 mb-2">90%</div>
            <div className="text-sm text-blue-700 dark:text-blue-300">Faster Time to Market</div>
            <p className="text-xs text-gray-500 mt-2">Self-service vs ticket-based provisioning</p>
          </div>
          <div className="bg-gradient-to-br from-purple-50 to-pink-50 dark:from-purple-900/20 dark:to-pink-900/20 rounded-2xl p-6 border border-purple-200 dark:border-purple-800 text-center">
            <div className="text-4xl font-bold text-purple-600 dark:text-purple-400 mb-2">100%</div>
            <div className="text-sm text-purple-700 dark:text-purple-300">Audit Compliance</div>
            <p className="text-xs text-gray-500 mt-2">Full GitOps trail for all changes</p>
          </div>
        </div>
      </Section>

      {/* Use Cases */}
      <Section title="Use Cases" subtitle="Common deployment scenarios">
        <div className="space-y-6">
          {useCases.map((useCase, index) => (
            <Card key={index}>
              <div className="flex items-start gap-4">
                <div className="p-3 bg-blue-100 dark:bg-blue-900/30 rounded-xl shrink-0">
                  <Building2 className="text-blue-600 dark:text-blue-400" size={24} />
                </div>
                <div className="flex-1">
                  <h3 className="font-semibold mb-2">{useCase.title}</h3>
                  <p className="text-gray-600 dark:text-gray-400 text-sm mb-4">{useCase.description}</p>
                  <div className="grid md:grid-cols-2 gap-4">
                    <div>
                      <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Teams</h4>
                      <div className="flex flex-wrap gap-2">
                        {useCase.teams.map((team) => (
                          <span key={team} className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-xs">
                            {team}
                          </span>
                        ))}
                      </div>
                    </div>
                    <div>
                      <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Benefits</h4>
                      <ul className="space-y-1">
                        {useCase.benefits.map((benefit) => (
                          <li key={benefit} className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
                            <CheckCircle2 size={14} className="text-green-500" />
                            {benefit}
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                </div>
              </div>
            </Card>
          ))}
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
