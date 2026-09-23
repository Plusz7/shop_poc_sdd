import { http, HttpResponse, type HttpHandler } from 'msw';
import type { Problem, Schemas } from '../../../src/api/client';

/** Absolute URL of an API path as the client sees it in jsdom. */
export const apiUrl = (path: string) => new URL(path, window.location.origin).toString();

/** An RFC 9457 response as sent by the backend. */
export function problem(
  status: number,
  body: Omit<Problem, 'status' | 'title'> & { title?: string },
) {
  return HttpResponse.json(
    { title: body.title ?? 'Error', status, ...body },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

export const emptyCart: Schemas['Cart'] = {
  lines: [],
  itemCount: 0,
  totalMinor: 0,
  canPlaceOrder: false,
  messages: [],
};

/** Default handlers - individual tests override them with server.use(...). */
export const handlers: HttpHandler[] = [
  http.get(apiUrl('/api/cart'), () => HttpResponse.json(emptyCart)),
  http.get(apiUrl('/api/categories'), () => HttpResponse.json([])),
  http.get(apiUrl('/api/products'), () =>
    HttpResponse.json({ products: [], page: 0, size: 24, totalElements: 0, totalPages: 0 }),
  ),
];
