/**
 * The typed error hierarchy. Every failure — HTTP, network or malformed
 * envelope — becomes one of these before it leaves the API layer, so no
 * component ever inspects a status code.
 */

export interface ApiErrorInit {
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
  formErrors?: string[];
  retryAfterSeconds?: number;
  requestId?: string;
  cause?: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  /** Parsed from errors[], keyed by the field the message refers to. */
  readonly fieldErrors: Record<string, string>;
  /** Object-level @AssertTrue failures, which name no field. */
  readonly formErrors: string[];
  readonly retryAfterSeconds?: number;
  readonly requestId?: string;

  constructor(init: ApiErrorInit) {
    super(init.message);
    this.name = new.target.name;
    this.status = init.status;
    this.fieldErrors = init.fieldErrors ?? {};
    this.formErrors = init.formErrors ?? [];
    if (init.retryAfterSeconds !== undefined) this.retryAfterSeconds = init.retryAfterSeconds;
    if (init.requestId !== undefined) this.requestId = init.requestId;
    if (init.cause !== undefined) this.cause = init.cause;
  }

  get hasFieldErrors(): boolean {
    return Object.keys(this.fieldErrors).length > 0;
  }
}

/** 400 carrying errors[] — machine-mappable onto form fields. */
export class ValidationError extends ApiError {}

/**
 * 400 with an empty errors[]. The message is a business rule written for a
 * human — "A pending pass cannot become expired. Allowed from here: active,
 * revoked." Render it verbatim; do not replace it with a generic string.
 */
export class BusinessRuleError extends ApiError {}

/** 401. The interceptor has already cleared the session by the time this lands. */
export class UnauthorizedError extends ApiError {}

/** 403. The session is fine and the action is not allowed. NEVER log out on this. */
export class ForbiddenError extends ApiError {}

export class NotFoundError extends ApiError {}
export class ConflictError extends ApiError {}

/** 429. Carries Retry-After. Never retried automatically — every one is deliberate. */
export class RateLimitError extends ApiError {}

export class ServerError extends ApiError {}
export class NetworkError extends ApiError {}

/** A 2xx body that was not an ApiResponse envelope. A backend contract break. */
export class MalformedResponseError extends ApiError {
  constructor(message: string, cause?: unknown) {
    super({ status: 0, message, cause });
  }
}

/** Thrown by tokenStore.refresh(). No refresh endpoint exists in the backend. */
export class NotSupportedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'NotSupportedError';
  }
}

/**
 * Split "field: message" into a field map.
 *
 * The backend emits three shapes:
 *   "visitorEmail: Enter a valid email address"        → field
 *   "submit.dto.purpose: must not be blank"            → last path segment
 *   "eventCreateDto: The end date cannot be before..." → object-level, no field
 *
 * The third is the one that matters: cross-field @AssertTrue failures name the
 * object, not a field, so they must surface as form-level errors rather than
 * being dropped because no input matches.
 */
export function parseFieldErrors(
  errors: readonly string[] | null | undefined,
): { fieldErrors: Record<string, string>; formErrors: string[] } {
  const fieldErrors: Record<string, string> = {};
  const formErrors: string[] = [];

  for (const entry of errors ?? []) {
    const separator = entry.indexOf(': ');
    if (separator === -1) {
      formErrors.push(entry);
      continue;
    }
    const path = entry.slice(0, separator);
    const message = entry.slice(separator + 2);
    const segments = path.split('.');
    const field = segments[segments.length - 1] ?? path;

    // A path whose last segment looks like a DTO class name (…Dto, …Request)
    // is an object-level violation, not a field.
    if (/(?:Dto|Request|dto)$/.test(field) || field === path && /Dto$/i.test(field)) {
      formErrors.push(message);
      continue;
    }
    if (!(field in fieldErrors)) fieldErrors[field] = message;
  }

  return { fieldErrors, formErrors };
}
