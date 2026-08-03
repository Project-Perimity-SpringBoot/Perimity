import axios, {
  type AxiosAdapter, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig,
} from 'axios';
import type { ServiceName } from '../api/serviceName';
import { HANDLERS, MockHttpError, type MockRequest } from './handlers';
import { logger } from '../logging/logger';

/**
 * Per-service mock toggle.
 *
 * `VITE_USE_MOCKS=true` turns all six on. A per-service flag overrides it, so a
 * member can flip their own service to the real backend the moment their
 * controllers land without waiting for anybody else:
 *
 *   VITE_USE_MOCKS=true
 *   VITE_USE_MOCKS_AUTH=false      ← auth is live, the other five are mocked
 *
 * That per-service granularity is the whole point. A single global switch means
 * nobody can integrate until everybody can.
 */
const FLAG: Record<ServiceName, string> = {
  auth: 'VITE_USE_MOCKS_AUTH',
  user: 'VITE_USE_MOCKS_USER',
  gatepass: 'VITE_USE_MOCKS_GATEPASS',
  campus: 'VITE_USE_MOCKS_CAMPUS',
  guard: 'VITE_USE_MOCKS_GUARD',
  qr: 'VITE_USE_MOCKS_QR',
};

export function isMocked(service: ServiceName): boolean {
  const specific = import.meta.env[FLAG[service]];
  if (specific === 'true') return true;
  if (specific === 'false') return false;
  return import.meta.env['VITE_USE_MOCKS'] === 'true';
}

/** Latency, so loading states and skeletons are actually exercised. */
const LATENCY_MS = 220;

function normalisePath(config: InternalAxiosRequestConfig): string {
  const raw = config.url ?? '';
  const withoutQuery = raw.split('?')[0] ?? '';
  return withoutQuery.replace(/\/+$/, '') || '/';
}

/**
 * Match a concrete path against the handler table, tolerating ids.
 *
 * Exact match first; then a pattern pass where any numeric segment may stand in
 * for any other. Without that second pass every id in the fixtures would need
 * its own handler key.
 */
function findHandler(service: ServiceName, method: string, path: string) {
  const table = HANDLERS[service];
  const exact = table[`${method} ${path}`];
  if (exact) return exact;

  const wanted = path.split('/');
  for (const key of Object.keys(table)) {
    const [keyMethod, keyPath] = key.split(' ');
    if (keyMethod !== method || !keyPath) continue;
    const parts = keyPath.split('/');
    if (parts.length !== wanted.length) continue;
    const matches = parts.every((part, index) => {
      const other = wanted[index];
      if (part === other) return true;
      return /^\d+$/.test(part) && /^\d+$/.test(other ?? '');
    });
    if (matches) return table[key];
  }
  return undefined;
}

export function createMockAdapter(service: ServiceName): AxiosAdapter {
  return async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    // Resolved lazily rather than captured at construction: axios picks its
    // default adapter from a list, and asking for it before the instance is
    // fully built is how you end up delegating to undefined.
    if (!isMocked(service)) return axios.getAdapter(['xhr', 'fetch', 'http'])(config);

    const method = (config.method ?? 'get').toUpperCase();
    const path = normalisePath(config);
    const handler = findHandler(service, method, path);

    if (!handler) {
      /*
       * A missing handler is NOT silently passed through to the network. In
       * mock mode the backend may not be running at all, and a connection
       * refused three layers down is far harder to read than this line.
       */
      logger.warn(`No mock handler for ${method} ${path}`, { service });
      return Promise.reject(
        Object.assign(new Error(`No mock handler for ${method} ${path}`), {
          config,
          response: {
            status: 501, statusText: 'Not Implemented', config, headers: {},
            data: {
              success: false,
              message: `No mock handler for ${method} ${path}. Add one in src/lib/mocks/handlers.ts.`,
              data: null, errors: [],
            },
          },
        }),
      );
    }

    let body: unknown = config.data;
    if (typeof body === 'string') {
      try { body = JSON.parse(body); } catch { /* FormData and blobs stay as-is */ }
    }

    const request: MockRequest = {
      method, path, body,
      params: (config.params ?? {}) as Record<string, unknown>,
    };

    await new Promise((resolve) => setTimeout(resolve, LATENCY_MS));

    // A handler may refuse. Reject the same shape axios would on a real 4xx so
    // the interceptor maps it to the typed error the forms already handle.
    let payload: unknown;
    try {
      payload = handler(request);
    } catch (thrown) {
      if (thrown instanceof MockHttpError) {
        return Promise.reject(
          Object.assign(new Error(`Request failed with status code ${thrown.status}`), {
            config,
            response: {
              status: thrown.status,
              statusText: 'Unauthorized',
              config,
              headers: {},
              data: thrown.payload,
            },
          }),
        );
      }
      throw thrown;
    }

    return {
      data: payload,
      status: 200,
      statusText: 'OK',
      headers: {},
      config: config as AxiosRequestConfig as InternalAxiosRequestConfig,
    };
  };
}
