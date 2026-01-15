export default function Section({ title, subtitle, children, className = '' }) {
  return (
    <section className={`mb-12 ${className}`}>
      {(title || subtitle) && (
        <div className="mb-6">
          {title && <h2 className="text-2xl font-bold">{title}</h2>}
          {subtitle && <p className="text-gray-600 dark:text-gray-400 mt-2">{subtitle}</p>}
        </div>
      )}
      {children}
    </section>
  )
}

export function SectionDivider() {
  return <hr className="my-12 border-gray-200 dark:border-gray-800" />
}
