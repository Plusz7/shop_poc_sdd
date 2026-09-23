import createClient from 'openapi-fetch';
import type { components, paths } from './schema';

export type Schemas = components['schemas'];
export type Problem = Schemas['Problem'] & { cart?: Schemas['Cart'] };
export type ErrorCode = Problem['code'];

/** Error thrown by {@link unwrap} for every non-2xx response; carries the parsed RFC 9457 problem. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem;

  constructor(status: number, problem: Problem) {
    super(problem.code ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  get code(): ErrorCode {
    return this.problem.code;
  }
}

/**
 * Typed client of the backend contract (API-first, research R-19). Same-origin only: in dev the
 * Vite proxy forwards /api to the backend, so the shop_guest cookie is first-party.
 */
export const api = createClient<paths>({
  baseUrl: typeof window === 'undefined' ? '' : window.location.origin,
  credentials: 'same-origin',
  // resolved per call, so test doubles installed after import are picked up
  fetch: (request: Request) => globalThis.fetch(request),
});

type FetchResult<T> = { data?: T; error?: unknown; response: Response };

/** Returns the response data or throws {@link ApiError} with the parsed problem. */
export async function unwrap<T>(call: Promise<FetchResult<T>>): Promise<T> {
  const { data, error, response } = await call;
  if (!response.ok) {
    throw new ApiError(response.status, toProblem(response.status, error));
  }
  return data as T;
}

function toProblem(status: number, body: unknown): Problem {
  if (body && typeof body === 'object' && 'code' in body) {
    return body as Problem;
  }
  return {
    type: 'about:blank',
    title: 'Error',
    status,
    code: status === 404 ? 'NOT_FOUND' : status >= 500 ? 'INTERNAL_ERROR' : 'VALIDATION_ERROR',
  };
}
