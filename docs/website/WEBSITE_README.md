# Messaging Operator Documentation Website

A beautifully designed, static documentation website built with React, Vite, and TailwindCSS. Features markdown rendering with syntax highlighting, dark mode support, and GitHub Pages deployment.

## Design Aesthetic

The website features a **refined editorial + technical monospace** aesthetic:

- **Typography**: JetBrains Mono for technical elements, Literata serif for body text
- **Color Palette**: Deep navy blues with amber/orange accents (inspired by technical manuals and blueprints)
- **Layout**: Magazine-inspired with generous whitespace and asymmetric composition
- **Interactions**: Smooth, purposeful animations that enhance the user experience

## Tech Stack

- **React 19** - UI framework
- **Vite** - Build tool and dev server
- **TailwindCSS 4** - Utility-first styling
- **react-markdown** - Markdown rendering
- **react-router-dom** - Client-side routing
- **remark-gfm** - GitHub Flavored Markdown support
- **rehype-highlight** - Syntax highlighting for code blocks
- **Lucide React** - Beautiful icon library
- **@xyflow/react** - Interactive flow diagrams

## Quick Start

### Development

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Open browser to http://localhost:5173
```

### Build for Production

```bash
# Build static site
npm run build

# Preview production build
npm run preview
```

### Deployment

The site automatically deploys to GitHub Pages when changes are pushed to the `main` branch in the `docs/website/` directory.

GitHub Actions workflow: `.github/workflows/deploy-docs.yml`

**Live URL**: `https://framiere.github.io/conduktor_quick_start_in_a_single_container/`

## Adding Markdown Content

### 1. Create a Markdown File

Place your markdown file in `public/docs/`:

```bash
public/
└── docs/
    ├── sample.md
    ├── getting-started.md
    └── advanced-usage.md
```

### 2. Create a Page Component

Create a new page in `src/pages/`:

```jsx
import MarkdownPage from '../components/MarkdownPage'

export default function GettingStartedPage() {
  return (
    <MarkdownPage
      title="Getting Started"
      description="Learn how to get started with the Messaging Operator"
      markdownFile="/docs/getting-started.md"
    />
  )
}
```

### 3. Add Route to App.jsx

Import and add the route:

```jsx
// Import at top
import GettingStartedPage from './pages/GettingStartedPage'

// Add to perspectives array
const perspectives = [
  // ... existing items
  {
    path: '/getting-started',
    name: 'Getting Started',
    icon: Rocket,  // Import from lucide-react
    description: 'Quick start guide'
  },
]

// Add route in Routes component
<Route path="/getting-started" element={<GettingStartedPage />} />
```

## Markdown Features

The markdown renderer supports:

### Text Formatting
- **Bold**, *italic*, ~~strikethrough~~
- `inline code`
- [Links](https://example.com)

### Headings
```markdown
# H1 - Main Title (with amber underline)
## H2 - Section Title
### H3 - Subsection
#### H4 - Minor heading
```

### Code Blocks
````markdown
```java
public class Example {
    public static void main(String[] args) {
        System.out.println("Hello World");
    }
}
```
````

Supports syntax highlighting for: Java, JavaScript, TypeScript, YAML, JSON, Bash, Python, Go, Rust, and more.

### Lists

```markdown
- Unordered list item 1
- Unordered list item 2
  - Nested item

1. Ordered list item 1
2. Ordered list item 2
```

### Tables

```markdown
| Column 1 | Column 2 | Column 3 |
|----------|----------|----------|
| Data 1   | Data 2   | Data 3   |
| Data 4   | Data 5   | Data 6   |
```

### Blockquotes

```markdown
> "We shape our tools, and thereafter our tools shape us."
>
> — Marshall McLuhan
```

### Images

```markdown
![Alt text](./images/diagram.png)
```

### Horizontal Rules

```markdown
---
```

## Project Structure

```
docs/website/
├── public/
│   ├── docs/              # Markdown files
│   │   └── sample.md
│   └── vite.svg
├── src/
│   ├── assets/            # Static assets
│   ├── components/        # React components
│   │   ├── Card.jsx
│   │   ├── CodeBlock.jsx
│   │   ├── MarkdownPage.jsx      # Markdown page wrapper
│   │   ├── MarkdownRenderer.jsx   # Markdown renderer
│   │   ├── PageLayout.jsx
│   │   └── Section.jsx
│   ├── data/              # JSON data
│   ├── pages/             # Page components
│   │   ├── HomePage.jsx
│   │   ├── ArchitecturePage.jsx
│   │   ├── DocsPage.jsx   # Example markdown page
│   │   └── ...
│   ├── App.jsx            # Main app with routing
│   ├── index.css          # Global styles
│   └── main.jsx           # Entry point
├── dist/                  # Build output (gitignored)
├── index.html
├── package.json
├── vite.config.js
└── WEBSITE_README.md      # This file
```

## Customization

### Typography

Fonts are defined in `index.html`:
- **JetBrains Mono** - Monospace/technical font
- **Literata** - Serif body font

To change fonts, update:
1. Font imports in `index.html`
2. Font family CSS in `src/index.css`

### Colors

Color palette defined in `src/index.css`:

```css
:root {
  --navy-950: #0a0e1a;
  --navy-900: #0f172a;
  --navy-800: #1e293b;
  --amber-500: #f59e0b;
  --amber-600: #d97706;
  --amber-400: #fbbf24;
}
```

TailwindCSS utilities: `bg-navy-900`, `text-amber-500`, etc.

### Markdown Styling

All markdown styling is in `src/components/MarkdownRenderer.jsx`.

Each HTML element is mapped to a styled React component:
- `h1`, `h2`, `h3`, `h4` - Headings with distinct hierarchy
- `p` - Paragraphs with readable line height
- `code`, `pre` - Code blocks with syntax highlighting
- `a` - Links with hover effects
- `table`, `th`, `td` - Tables with alternating rows
- `blockquote` - Quoted text with left border
- `ul`, `ol`, `li` - Lists with custom bullets

## Dark Mode

Dark mode is automatically detected from system preferences and can be toggled in the sidebar.

Theme state is managed in `App.jsx` and applied via the `dark` class on the root element.

## Navigation

The sidebar navigation is defined in `App.jsx`:

```jsx
const perspectives = [
  {
    path: '/',
    name: 'Overview',
    icon: Home,
    description: 'Project introduction'
  },
  // ... more items
]
```

Each item requires:
- `path` - Route path
- `name` - Display name
- `icon` - Lucide React icon component
- `description` - Short description shown when active

## Performance

The build outputs a single-page application with:
- **Code splitting**: React Router handles lazy loading
- **Asset optimization**: Vite optimizes CSS and JS
- **Font loading**: Preconnect to Google Fonts for faster loading
- **Image optimization**: Use optimized images in `public/` directory

## GitHub Pages Configuration

### Vite Config

```js
export default defineConfig({
  base: './', // Relative paths for GitHub Pages
  build: {
    outDir: 'dist',
    assetsDir: 'assets'
  }
})
```

### GitHub Actions

The workflow in `.github/workflows/deploy-docs.yml`:
1. Triggers on push to `main` branch (when `docs/website/**` changes)
2. Installs dependencies
3. Builds the site
4. Deploys to GitHub Pages

### Manual Deployment

You can also deploy manually using the GitHub CLI:

```bash
# Build the site
npm run build

# Deploy using gh command
cd ../..
gh workflow run deploy-docs.yml
```

Or enable GitHub Pages in repository settings:
1. Go to Settings → Pages
2. Source: GitHub Actions
3. The workflow will handle deployments

## Troubleshooting

### Build Warnings

**Chunk size warning**: The bundle is large due to React Flow and markdown libraries. This is acceptable for documentation sites. To reduce size, consider code splitting.

### Font Loading Issues

Fonts are loaded from Google Fonts. If fonts don't load:
1. Check network connectivity
2. Verify font names in `index.html`
3. Ensure font names match in CSS

### Markdown Not Rendering

If markdown doesn't render:
1. Check file path in `markdownFile` prop (must be relative to `public/`)
2. Verify file exists in `public/docs/`
3. Check browser console for fetch errors

### Dark Mode Issues

Dark mode uses Tailwind's `dark:` variant. Ensure:
1. `dark` class is on root element in `App.jsx`
2. Dark mode styles use `dark:` prefix
3. Browser supports `prefers-color-scheme` media query

## Contributing

When adding new pages or features:
1. Follow the existing aesthetic and component patterns
2. Use JetBrains Mono for technical/mono elements
3. Use Literata for body text
4. Maintain the navy + amber color scheme
5. Ensure dark mode support
6. Test responsive design (mobile, tablet, desktop)
7. Add meaningful micro-interactions where appropriate

## License

This documentation website is part of the Messaging Operator project.
