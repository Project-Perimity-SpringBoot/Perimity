import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Port 5173 is not arbitrary. Every service's SecurityConfig lists
// http://localhost:3000 and http://localhost:5173 as allowed CORS origins.
// Run the dev server anywhere else and every call fails preflight - which
// looks exactly like a broken endpoint and is not one.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
});
