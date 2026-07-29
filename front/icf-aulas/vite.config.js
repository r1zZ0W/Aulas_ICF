import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  optimizeDeps: {
    include: ['jspdf', 'html2canvas-pro'],
  },
  server: {
    port: 5173,
    open: true,
  },
  // Relative on purpose: works when served from the domain root or from a subpath (e.g. the
  // reverse-proxy Alias /~aulas_icf) without a build-time constant. Only safe because every
  // route in routes.meta.js is a single path segment (e.g. /reports, not /reports/2026) — a
  // relative "./assets/x.js" resolves against the LAST segment of the current URL, so it
  // drops exactly one level. Adding a route nested two levels deep (e.g. /reports/:id) would
  // break asset loading on refresh; re-evaluate this if that happens. Router basename is
  // handled separately, via VITE_ROUTER_BASENAME (see src/main.jsx) — it does NOT depend on
  // this value.
  base: './',
});

