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
                target: 'http://localhost:2637',
                changeOrigin: true,
                secure: false,
                cookieDomainRewrite: 'localhost',
                configure: (proxy, _options) => {
                    proxy.on('proxyReq', (proxyReq, req, _res) => {
                        // Forward cookies from the original request
                        if (req.headers.cookie) {
                            proxyReq.setHeader('Cookie', req.headers.cookie);
                        }
                    });
                },
            },
        },
    },
})
