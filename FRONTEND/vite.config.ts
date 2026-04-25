import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        allowedHosts: ['hokeka.com', 'www.hokeka.com', 'fraud.hokeka.com', 'localhost', '127.0.0.1'],
        proxy: {
            '/api/v1': {
                target: process.env.VITE_PROXY_TARGET || 'http://localhost:2637',
                changeOrigin: true,
                secure: false,
                cookieDomainRewrite: 'localhost',
                configure: (proxy, _options) => {
                    proxy.on('proxyReq', (proxyReq, req, _res) => {
                        if (req.headers.cookie) {
                            proxyReq.setHeader('Cookie', req.headers.cookie);
                        }
                    });
                },
            },
        },
    },
    build: {
        rollupOptions: {
            output: {
                manualChunks: {
                    // React core — always loaded
                    'vendor-react': ['react', 'react-dom', 'react-router-dom'],
                    // MUI — always loaded (large, shared by all pages)
                    'vendor-mui': ['@mui/material', '@mui/icons-material', '@emotion/react', '@emotion/styled'],
                    // Data fetching — loaded by all pages
                    'vendor-query': ['@tanstack/react-query'],
                    // Charts — only loaded by analytics pages
                    'vendor-charts': ['chart.js', 'react-chartjs-2', 'recharts'],
                },
            },
        },
    },
})
