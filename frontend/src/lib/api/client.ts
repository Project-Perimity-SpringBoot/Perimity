import axios, { type AxiosInstance } from 'axios';
import { attachInterceptors } from './interceptors';
import { createMockAdapter } from '../mocks/mockAdapter';
import type { ServiceName } from './serviceName';

/**
 * Six axios instances from one factory.
 *
 * There is no API Gateway (blocker B3). In proxy mode all six share one origin
 * and the disjoint path prefixes do the routing, which is why every VITE_*_URL
 * defaults to "/api". Direct mode exists so a teammate can point at one service
 * in isolation while debugging.
 */

const DEFAULT_TIMEOUT_MS = 15_000;
/** Bulk validation parses the whole sheet synchronously before answering. */
const UPLOAD_TIMEOUT_MS = 60_000;

/**
 * The ORIGIN a service is reached on — never a path prefix.
 *
 * Every service module already carries the full path (`/api/auth/login`,
 * `/api/gatepass/passes`). Returning `/api` here made axios join the two into
 * `/api/api/auth/login`, which matches no proxy rule, so the Vite dev server
 * answered with its own 404 page. Mocks hid it completely: the mock adapter
 * matches on `config.url`, which never had the baseURL applied.
 *
 *   proxy  → '' — same origin, and the disjoint path prefixes do the routing
 *   direct → http://localhost:808x — one service in isolation, for debugging
 *
 * A value in VITE_*_URL overrides both, and must be an origin with no trailing
 * path: `http://localhost:8081`, not `http://localhost:8081/api`.
 */
function baseUrl(envValue: string | undefined, fallbackPort: number): string {
  // '/api' was the documented value in an earlier .env.example. Treated as
  // "proxy mode" rather than silently producing a doubled prefix, so an old
  // .env.local keeps working instead of 404ing in a way nobody can read.
  if (envValue && envValue.length > 0 && envValue !== '/api') return envValue;
  return import.meta.env['VITE_API_MODE'] === 'direct'
    ? `http://${import.meta.env['VITE_BACKEND_HOST'] ?? 'localhost'}:${fallbackPort}`
    : '';
}

function createClient(name: ServiceName, url: string, timeout = DEFAULT_TIMEOUT_MS): AxiosInstance {
  const instance = axios.create({
    baseURL: url,
    timeout,
    headers: { Accept: 'application/json' },
  });

  /*
   * The mock layer sits at the ADAPTER, below the interceptors, so a mocked
   * response still travels through normalize.ts, the error hierarchy and the
   * 401 handling exactly as a real one does. Mocking above the interceptors
   * would leave the layer the mocks exist to exercise untested.
   */
  instance.defaults.adapter = createMockAdapter(name);

  attachInterceptors(instance, name);
  return instance;
}

const env = import.meta.env;

export const authClient = createClient('auth', baseUrl(env['VITE_AUTH_URL'], 8081));
export const userClient = createClient('user', baseUrl(env['VITE_USER_URL'], 8082), UPLOAD_TIMEOUT_MS);
export const gatepassClient = createClient('gatepass', baseUrl(env['VITE_GATEPASS_URL'], 8083), UPLOAD_TIMEOUT_MS);
export const campusClient = createClient('campus', baseUrl(env['VITE_CAMPUS_URL'], 8084));
export const guardClient = createClient('guard', baseUrl(env['VITE_GUARD_URL'], 8085));
export const qrClient = createClient('qr', baseUrl(env['VITE_QR_URL'], 8086));

export const clients = {
  auth: authClient,
  user: userClient,
  gatepass: gatepassClient,
  campus: campusClient,
  guard: guardClient,
  qr: qrClient,
} as const;

export type { ServiceName };
