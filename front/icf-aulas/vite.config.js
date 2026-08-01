import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Emits a <link rel="preload"> for the self-hosted DM Sans .woff2 (@fontsource-variable/dm-sans,
// imported from src/main.jsx). Its CSS is extracted by Vite into the main stylesheet and linked
// in <head> automatically, so the browser discovers the @font-face without running JS — but it
// still can't start fetching the font file itself until it parses that CSS. Preloading removes
// that extra round trip. Can't hardcode the href: Fontsource's file name is content-hashed.
function preloadDmSans() {
  let configBase = './';
  return {
    name: 'preload-dm-sans',
    configResolved(config) {
      configBase = config.base;
    },
    transformIndexHtml: {
      order: 'post',
      handler(html, ctx) {
        // Must match "latin" specifically, not "latin-ext": the wght.css from
        // @fontsource-variable/dm-sans emits BOTH as separate @font-face rules (one per
        // unicode-range), and Spanish text only ever triggers the "latin" one. An earlier,
        // looser regex here matched "latin-ext" first (found via Object.keys iteration
        // order) and preloaded 18 KB the browser was never going to fetch, while the actual
        // 36.9 KB "latin" file it does fetch got no preload at all — worse than doing
        // nothing.
        const file = Object.keys(ctx.bundle ?? {}).find((f) => /dm-sans-latin-wght-normal-[^/]*\.woff2$/i.test(f));
        if (!file) return html; // dev server: no bundle yet, the CSS is already inlined
        const href = `${configBase.endsWith('/') ? configBase : `${configBase}/`}${file}`;
        return {
          html,
          tags: [{
            tag: 'link',
            injectTo: 'head',
            attrs: { rel: 'preload', as: 'font', type: 'font/woff2', crossorigin: '', href },
          }],
        };
      },
    },
  };
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), preloadDmSans()],
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

