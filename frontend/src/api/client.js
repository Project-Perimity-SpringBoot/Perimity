import axios from 'axios';

/**
 * One axios instance per service, and the two interceptors every teammate
 * inherits without writing them.
 *
 * WHY SIX INSTANCES AND NOT ONE
 * There is no API gateway. Each service answers on its own port, so a single
 * baseURL cannot address all of them. When the gateway lands, every URL below
 * becomes the same host and no page changes.
 */
const TOKEN_KEY = 'perimity.token';

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

/**
 * Every service answers in the same envelope:
 *
 *     { success, message, data, errors }
 *
 * THE RESPONSE INTERCEPTOR UNWRAPS IT, so your page receives `data` directly
 * and never writes `res.data.data`. That double-data is the single most common
 * thing to get wrong against this backend, and doing it here means nobody has
 * to get it right five more times.
 */
function unwrap(response) {
  const body = response.data;
  if (body && typeof body === 'object' && 'success' in body && 'data' in body) {
    return body.data;
  }
  return body; // health endpoints and anything not using ApiResponse
}

/**
 * Turns any failure into one Error with a message worth showing a human.
 *
 * The backend puts field errors in `errors` and a summary in `message`, so a
 * validation failure reads "Validation failed: email: Enter a valid email
 * address" rather than "Request failed with status code 400".
 */
function toError(error) {
  const body = error.response?.data;
  const parts = [];
  if (body?.message) parts.push(body.message);
  if (Array.isArray(body?.errors) && body.errors.length) parts.push(body.errors.join('; '));

  const e = new Error(parts.join(': ') || error.message || 'Something went wrong');
  e.status = error.response?.status;
  e.raw = body;
  return e;
}

/** Set by AuthContext so a 401 can end the session from inside the interceptor. */
let onUnauthorized = () => {};
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn;
}

function build(baseURL) {
  const instance = axios.create({ baseURL, timeout: 10000 });

  instance.interceptors.request.use((config) => {
    const token = tokenStore.get();
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  });

  instance.interceptors.response.use(
    (response) => unwrap(response),
    (error) => {
      /*
       * 401 means the token is gone, expired, or logged out - so end the
       * session rather than letting the user click into five more failures.
       *
       * The login and OTP calls are excluded. They answer 401 for a WRONG
       * PASSWORD, and treating that as a session expiry would clear a session
       * that never existed and bounce the user off the page they are trying
       * to log in on.
       */
      const url = error.config?.url || '';
      const isLoginAttempt = url.includes('/login') || url.includes('/otp/');
      if (error.response?.status === 401 && !isLoginAttempt) {
        onUnauthorized();
      }
      return Promise.reject(toError(error));
    },
  );

  return instance;
}

export const authApi = build(import.meta.env.VITE_AUTH_URL);
export const userApi = build(import.meta.env.VITE_USER_URL);
export const gatepassApi = build(import.meta.env.VITE_GATEPASS_URL);
export const campusApi = build(import.meta.env.VITE_CAMPUS_URL);
export const guardApi = build(import.meta.env.VITE_GUARD_URL);
export const qrApi = build(import.meta.env.VITE_QR_URL);
