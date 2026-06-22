import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export function resolveApiProxyTarget(env: { VITE_API_PROXY_TARGET?: string } = process.env): string {
  return env.VITE_API_PROXY_TARGET || 'http://localhost:8080';
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': resolveApiProxyTarget(),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
  },
});
