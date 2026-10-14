import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'

/** Keep module scripts at the document end and drop modulepreload hints for faster LCP text. */
function lcpHtmlPlugin(): Plugin {
  return {
    name: 'hokeka-lcp-html',
    apply: 'build',
    transformIndexHtml: {
      order: 'post',
      handler(html) {
        const scripts = [...html.matchAll(/<script type="module" crossorigin src="[^"]+"><\/script>/g)].map((m) => m[0])
        let out = html.replace(/<script type="module" crossorigin src="[^"]+"><\/script>\n?/g, '')
        out = out.replace(/<link rel="modulepreload" crossorigin href="[^"]+">\n?/g, '')
        if (scripts.length) {
          out = out.replace('</body>', `${scripts.join('\n')}\n</body>`)
        }
        return out
      },
    },
  }
}

export default defineConfig({
  plugins: [react(), lcpHtmlPlugin()],
  server: {
    port: 4173,
    proxy: {
      '/api/v1': {
        target: 'http://127.0.0.1:2637',
        changeOrigin: true,
      },
    },
  },
  build: {
    modulePreload: false,
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-react': ['react', 'react-dom'],
          'vendor-motion': ['motion'],
          'vendor-gsap': ['gsap'],
          'vendor-lenis': ['lenis'],
        },
      },
    },
  },
})
