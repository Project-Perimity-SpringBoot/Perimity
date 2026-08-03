import type { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import { tokenStore } from '@lib/auth/tokenStore';
import { logger } from '@lib/logging/logger';
import {
  ApiError, BusinessRuleError, ConflictError, ForbiddenError, NetworkError,
  NotFoundError, RateLimitError, ServerError, UnauthorizedError,
  ValidationError, parseFieldErrors,
} from './errors';

/** Endpoints where a 401 is the answer, not a dead session. */
const AUTH_ENDPOINTS = [
  '/api/auth/login',
  /*
   * Logout belongs here. Without it, a 401 from the logout call itself — an
   * already-denylisted token, a token that expired seconds earlier — is read as
   * "the session died unexpectedly", which clears the token mid-call and fires
   * the session-expired redirect to /login. The user asked to sign out and got
   * a sign-in form and an "your session ended" banner instead.
   */
  '/api/auth/logout',
  '/api/auth/otp/request',
  '/api/auth/otp/verify',
  '/api/auth/visitors/register',
  '/api/auth/password/reset-request',
  '/api/auth/password/reset-confirm',
];

type SessionExpiredHandler = () => void;
let onSessionExpired: SessionExpiredHandler = () => {};

/** Wired by the app so the API layer never imports the router. */
export function setSessionExpiredHandler(handler: SessionExpiredHandler): void {
  onSessionExpired = handler;
}

function requestId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `req-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function isAuthEndpoint(url: string | undefined): boolean {
  return !!url && AUTH_ENDPOINTS.some((path) => url.includes(path));
}

interface TimedConfig extends InternalAxiosRequestConfig {
  metadata?: { start: number; id: string };
}

export function attachInterceptors(instance: AxiosInstance, service: string): void {
  instance.interceptors.request.use((config: TimedConfig) => {
    const id = requestId();
    config.metadata = { start: performance.now(), id };
    config.headers.set('X-Request-Id', id);

    const token = tokenStore.get();
    if (token) config.headers.set('Authorization', `Bearer ${token}`);

    // Let the browser set the multipart boundary. Setting Content-Type by hand
    // on FormData produces a request the server cannot parse.
    if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
      config.headers.delete('Content-Type');
    }

    // X-Internal-Api-Key must never exist in the browser bundle. Not set here,
    // and CI greps dist/ to prove it never appears.
    return config;
  });

  instance.interceptors.response.use(
    (response) => response,
    (error: AxiosError) => Promise.reject(toTypedError(error, service)),
  );
}

function toTypedError(error: AxiosError, service: string): ApiError {
  const config = error.config as TimedConfig | undefined;
  const id = config?.metadata?.id;
  const path = config?.url;
  const method = config?.method?.toUpperCase();

  if (!error.response) {
    logger.error('Network failure', { service, method, path, requestId: id, errorClass: 'NetworkError' });
    return new NetworkError({
      status: 0,
      message: 'Could not reach the server. Check your connection and try again.',
      ...(id !== undefined ? { requestId: id } : {}),
      cause: error,
    });
  }

  const { status, headers, data } = error.response;
  const body = (typeof data === 'object' && data !== null ? data : {}) as {
    message?: string; errors?: string[];
  };
  const message = body.message ?? fallbackMessage(status);
  const { fieldErrors, formErrors } = parseFieldErrors(body.errors);
  const base = {
    status,
    message,
    fieldErrors,
    formErrors,
    ...(id !== undefined ? { requestId: id } : {}),
  };

  logger.warn('Request failed', {
    service, method, path, status, requestId: id, errorClass: String(status),
  });

  switch (status) {
    case 400:
      // A 400 with errors[] is machine-mappable onto fields. A 400 without one
      // carries a business message written for a human — keep them apart.
      return body.errors && body.errors.length > 0
        ? new ValidationError(base)
        : new BusinessRuleError(base);

    case 401: {
      if (!isAuthEndpoint(path)) {
        tokenStore.clear();
        onSessionExpired();
      }
      return new UnauthorizedError(base);
    }

    // Never log out here. A 403 means the session is fine and the action is
    // not allowed; logging out is the bug that makes an app feel broken.
    case 403:
      return new ForbiddenError(base);

    case 404:
      return new NotFoundError(base);

    case 409:
      return new ConflictError(base);

    case 429: {
      const header = headers['retry-after'];
      const seconds = Number(header);
      return new RateLimitError({
        ...base,
        retryAfterSeconds: Number.isFinite(seconds) ? seconds : 60,
      });
    }

    default:
      return new ServerError(base);
  }
}

function fallbackMessage(status: number): string {
  if (status >= 500) return 'The server could not complete that request. Try again shortly.';
  if (status === 404) return 'That record no longer exists.';
  if (status === 403) return 'Your role is not permitted to perform this action.';
  if (status === 401) return 'Authentication required.';
  return 'That request could not be completed.';
}
