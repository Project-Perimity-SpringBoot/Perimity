import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { fileURLToPath, URL } from 'node:url';

/**
 * There is no API Gateway. The six services own disjoint path prefixes, so one
 * dev origin routes all of them by prefix alone — which makes CORS a non-issue
 * locally and gives production nginx the same six rules.
 */
const SERVICE_PORTS: Record<string, number> = {
  '/api/auth': 8081,
  '/api/user': 8082,
  '/api/gatepass': 8083,
  '/api/campus': 8084,
  '/api/guard': 8085,
  '/api/qr': 8086,
};

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const host = env['VITE_BACKEND_HOST'] ?? 'localhost';

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
        '@lib': fileURLToPath(new URL('./src/lib', import.meta.url)),
        '@ui': fileURLToPath(new URL('./src/design-system/primitives', import.meta.url)),
        '@components': fileURLToPath(new URL('./src/components', import.meta.url)),
        '@features': fileURLToPath(new URL('./src/features', import.meta.url)),
        '@hooks': fileURLToPath(new URL('./src/hooks', import.meta.url)),
        '@stores': fileURLToPath(new URL('./src/stores', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      proxy: Object.fromEntries(
        Object.entries(SERVICE_PORTS).map(([path, port]) => [
          path,
          { target: `http://${host}:${port}`, changeOrigin: true },
        ]),
      ),
    },
    build: {
      target: 'es2022',
      sourcemap: false,
      chunkSizeWarningLimit: 700,
      rollupOptions: {
        output: {
          manualChunks(id) {
            // Match a package by its exact node_modules segment. A loose
            // `includes()` bucketed unrelated shared helpers into the wrong
            // chunk and dragged react-hook-form into the entry graph.
            const match = /node_modules\/(?:\.pnpm\/)?((?:@[^/]+\/)?[^/]+)/.exec(id);
            const pkg = match?.[1];
            if (!pkg) return undefined;
            if (pkg === 'react' || pkg === 'react-dom' || pkg === 'scheduler' || pkg === 'react-router') return 'react';
            if (pkg.startsWith('@radix-ui')) return 'radix';
            if (pkg === '@tanstack/react-query' || pkg === '@tanstack/query-core') return 'query';
            return undefined;
          },
        },
      },
    },
  };
});
