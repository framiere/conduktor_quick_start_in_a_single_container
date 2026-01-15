# GitHub Pages Deployment Guide

This guide explains how to deploy the Messaging Operator documentation website to GitHub Pages.

## Automatic Deployment (Recommended)

The site automatically deploys to GitHub Pages when changes are pushed to the `main` branch.

### Setup Steps

1. **Enable GitHub Pages in Repository Settings**

   ```bash
   # Navigate to repository on GitHub
   # Settings → Pages → Source → GitHub Actions
   ```

2. **Push Changes to Main Branch**

   ```bash
   cd docs/website
   git add .
   git commit -m "feat(docs): update documentation website"
   git push origin main
   ```

3. **Workflow Runs Automatically**

   The GitHub Actions workflow at `.github/workflows/deploy-docs.yml` will:
   - Build the site with `npm run build`
   - Upload the `dist/` folder as artifact
   - Deploy to GitHub Pages

4. **Access Your Site**

   Your site will be available at:
   ```
   https://framiere.github.io/conduktor_quick_start_in_a_single_container/
   ```

### Workflow Details

The workflow triggers on:
- Push to `main` branch
- Changes in `docs/website/**` paths
- Manual workflow dispatch

**Workflow file**: `.github/workflows/deploy-docs.yml`

```yaml
name: Deploy Documentation to GitHub Pages

on:
  push:
    branches: [main]
    paths: ['docs/website/**', '.github/workflows/deploy-docs.yml']
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - Checkout code
      - Setup Node.js 20
      - Install dependencies (npm ci)
      - Build site (npm run build)
      - Upload artifact

  deploy:
    runs-on: ubuntu-latest
    needs: build
    steps:
      - Deploy to GitHub Pages
```

## Manual Deployment

You can also trigger deployment manually using the GitHub CLI:

```bash
# Ensure you're in the repository root
cd /path/to/conduktor_quick_start_in_a_single_container

# Trigger workflow manually
gh workflow run deploy-docs.yml
```

Or via GitHub UI:
1. Go to Actions tab in GitHub
2. Select "Deploy Documentation to GitHub Pages" workflow
3. Click "Run workflow"
4. Select branch (main)
5. Click "Run workflow"

## Local Testing

Before deploying, test the production build locally:

```bash
# Build for production
npm run build

# Preview production build
npm run preview

# Opens at http://localhost:4173
```

This ensures:
- No build errors
- All assets load correctly
- Links work properly
- Dark mode functions
- Markdown renders correctly

## Deployment Checklist

Before pushing to main:

- [ ] Run `npm run build` successfully
- [ ] Test with `npm run preview`
- [ ] Check all pages load
- [ ] Verify markdown rendering
- [ ] Test dark/light mode toggle
- [ ] Check responsive design (mobile/tablet/desktop)
- [ ] Verify all links work
- [ ] Check code syntax highlighting
- [ ] Test navigation between pages
- [ ] Review browser console for errors

## Troubleshooting

### Deployment Fails

**Check workflow logs:**
```bash
# View recent workflow runs
gh run list --workflow=deploy-docs.yml

# View specific run logs
gh run view <run-id> --log
```

**Common issues:**
- Build errors: Check `npm run build` locally
- Permission errors: Verify GitHub Pages is enabled in settings
- Path issues: Ensure `base: './'` in `vite.config.js`

### Site Not Updating

**Clear cache:**
- Hard refresh: `Ctrl+F5` (Windows) or `Cmd+Shift+R` (Mac)
- Clear browser cache
- Wait 1-2 minutes for GitHub CDN to update

**Check deployment:**
```bash
# View deployment status
gh workflow view deploy-docs.yml

# Check if deployment completed
gh api repos/framiere/conduktor_quick_start_in_a_single_container/pages/builds/latest
```

### 404 on Refresh

Single-page apps on GitHub Pages may show 404 on direct URL access. This is why we use HashRouter (`#/path`) instead of BrowserRouter.

If you see 404s:
- Ensure `HashRouter` is used in `App.jsx` (not `BrowserRouter`)
- URLs should include hash: `/#/architecture` not `/architecture`

### Assets Not Loading

**Check base path:**
```js
// vite.config.js
export default defineConfig({
  base: './', // Use relative paths
})
```

**Verify asset paths:**
- Assets should be in `public/` directory
- Reference as `/path/to/asset` in code
- They'll be at `./path/to/asset` in production

## Custom Domain (Optional)

To use a custom domain:

1. **Add CNAME file**
   ```bash
   echo "docs.your-domain.com" > public/CNAME
   ```

2. **Configure DNS**
   Add CNAME record pointing to:
   ```
   framiere.github.io
   ```

3. **Enable HTTPS**
   GitHub Pages automatically provides HTTPS for custom domains.

4. **Update base path** (if needed)
   ```js
   // vite.config.js
   export default defineConfig({
     base: '/', // Root domain
   })
   ```

## Monitoring

**View deployment status:**
```bash
# List recent runs
gh run list --workflow=deploy-docs.yml --limit 5

# Watch current run
gh run watch
```

**Check site health:**
```bash
# Simple health check
curl -I https://framiere.github.io/conduktor_quick_start_in_a_single_container/

# Should return: HTTP/2 200
```

## Rollback

To rollback to a previous version:

```bash
# Find commit hash of previous working version
git log --oneline docs/website/

# Revert to that commit
git revert <commit-hash>

# Push to trigger new deployment
git push origin main
```

Or redeploy from a specific commit:

```bash
# Checkout previous commit
git checkout <commit-hash>

# Force push to main (use with caution!)
git push origin HEAD:main --force
```

## Performance Optimization

For faster deployments:

1. **Code splitting**: Use dynamic imports
   ```jsx
   const HomePage = lazy(() => import('./pages/HomePage'))
   ```

2. **Optimize images**: Compress before adding to `public/`

3. **Reduce bundle size**: Review `npm run build` output

4. **CDN caching**: GitHub Pages automatically caches assets

## Security

GitHub Pages deployments are secure by default:

- **HTTPS**: Enforced automatically
- **No secrets in code**: Never commit API keys or tokens
- **Read-only**: Deployment key has limited permissions
- **Isolated**: Runs in sandboxed environment

## Support

If you encounter issues:

1. Check workflow logs: `gh run view <run-id> --log`
2. Review GitHub Pages documentation
3. Test locally: `npm run build && npm run preview`
4. Check repository settings: Settings → Pages
5. Verify workflow permissions: Settings → Actions → General

## Additional Resources

- [GitHub Pages Documentation](https://docs.github.com/en/pages)
- [Vite Static Deploy Guide](https://vitejs.dev/guide/static-deploy.html)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Custom Domain Setup](https://docs.github.com/en/pages/configuring-a-custom-domain-for-your-github-pages-site)
