# GitHub Pages Setup Instructions

Quick guide to enable GitHub Pages for your documentation website.

## Method 1: GitHub Web UI (Easiest)

1. **Navigate to Repository Settings**
   - Go to: https://github.com/framiere/conduktor_quick_start_in_a_single_container
   - Click "Settings" tab
   - Click "Pages" in left sidebar

2. **Configure Source**
   - Under "Build and deployment"
   - Source: Select **"GitHub Actions"**
   - Save

3. **Trigger First Deployment**
   ```bash
   cd docs/website
   git add .
   git commit -m "feat(docs): setup GitHub Pages deployment"
   git push origin main
   ```

4. **Access Your Site**
   - Wait 2-3 minutes for deployment
   - Visit: https://framiere.github.io/conduktor_quick_start_in_a_single_container/

## Method 2: GitHub CLI

If you have `gh` CLI installed and authenticated:

```bash
# Enable GitHub Pages with GitHub Actions source
gh api -X POST repos/framiere/conduktor_quick_start_in_a_single_container/pages \
  -f source[branch]=main \
  -f source[path]=/

# Trigger workflow
gh workflow run deploy-docs.yml
```

## Method 3: Manual First Deploy

Build and commit the static site:

```bash
cd docs/website

# Build production site
npm run build

# Commit changes
git add .
git commit -m "feat(docs): add documentation website"
git push origin main
```

The GitHub Actions workflow will automatically deploy on push.

## Verify Setup

1. **Check Workflow Status**
   ```bash
   # View workflow runs
   gh workflow view deploy-docs.yml

   # Or visit GitHub Actions tab
   # https://github.com/framiere/conduktor_quick_start_in_a_single_container/actions
   ```

2. **Check Deployment**
   ```bash
   # Using curl
   curl -I https://framiere.github.io/conduktor_quick_start_in_a_single_container/

   # Should return HTTP 200
   ```

3. **Test in Browser**
   - Open: https://framiere.github.io/conduktor_quick_start_in_a_single_container/
   - Navigate through pages
   - Test dark mode toggle
   - Verify markdown rendering

## Configuration Already Complete

The following are already configured in this project:

✅ Vite config with relative base path
✅ GitHub Actions workflow file
✅ HashRouter for SPA routing
✅ Markdown rendering support
✅ Responsive design
✅ Dark mode support

**All you need to do is enable GitHub Pages in repository settings!**

## Troubleshooting

### "GitHub Pages not found" Error

This means Pages isn't enabled yet. Follow Method 1 above.

### Workflow Doesn't Run

Check workflow permissions:
1. Go to Settings → Actions → General
2. Scroll to "Workflow permissions"
3. Ensure "Read and write permissions" is selected
4. Enable "Allow GitHub Actions to create and approve pull requests"

### 404 Error on Site

1. Ensure GitHub Pages source is set to "GitHub Actions"
2. Check workflow completed successfully in Actions tab
3. Wait 2-3 minutes for CDN propagation
4. Try hard refresh: Ctrl+F5 (Windows) or Cmd+Shift+R (Mac)

## Next Steps

Once GitHub Pages is enabled:

1. **Test the Site**: Visit the URL and explore
2. **Add Content**: Create markdown files in `public/docs/`
3. **Customize**: Update colors, fonts, or layout
4. **Monitor**: Watch deployments in Actions tab

## Quick Reference

| Action | Command |
|--------|---------|
| Build site | `npm run build` |
| Preview locally | `npm run preview` |
| Deploy | `git push origin main` |
| Check status | Visit Actions tab on GitHub |
| View site | https://framiere.github.io/conduktor_quick_start_in_a_single_container/ |

## Support

Need help?
- Review `WEBSITE_README.md` for detailed documentation
- Check `DEPLOYMENT.md` for deployment troubleshooting
- View GitHub Pages docs: https://docs.github.com/en/pages
