import { act, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Schemas } from '../../../src/api/client';
import { apiUrl, problem } from '../msw/handlers';
import { server } from '../msw/server';
import { renderWithProviders } from '../renderWithProviders';

type Order = Schemas['Order'];

const NUMBER = 'ORD-7K2Q9M4XTB';

function order(status: Order['status']): Order {
  return {
    number: NUMBER,
    status,
    lines: [
      { name: 'Kubek ceramiczny Łódź', unitPriceMinor: 4990, quantity: 2, lineTotalMinor: 9980 },
    ],
    totalMinor: 9980,
    createdAt: '2026-09-25T10:00:00Z',
    paidAt:
      status === 'AWAITING_PAYMENT' || status === 'PAYMENT_FAILED' ? null : '2026-09-25T10:01:00Z',
  };
}

/** Answers with the given statuses in turn (the last one repeats) and counts the requests. */
function serveOrder(...statuses: Order['status'][]) {
  let requests = 0;
  server.use(
    http.get(apiUrl(`/api/orders/${NUMBER}`), () => {
      const status = statuses[Math.min(requests, statuses.length - 1)];
      requests += 1;
      return HttpResponse.json(order(status));
    }),
  );
  return { count: () => requests };
}

async function advance(ms: number) {
  await act(() => vi.advanceTimersByTimeAsync(ms));
}

describe('order confirmation page', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows the number, lines and total of the order', async () => {
    serveOrder('PAID');

    renderWithProviders(`/orders/${NUMBER}?session_id=cs_test_1`);

    expect(await screen.findByText(`Numer zamówienia: ${NUMBER}`)).toBeInTheDocument();
    expect(screen.getByText('Kubek ceramiczny Łódź')).toBeInTheDocument();
    expect(screen.getAllByText(/99,80\s*zł/).length).toBeGreaterThan(0);
  });

  it('polls every 2 s while the payment is being verified and shows the paid status', async () => {
    const backend = serveOrder('AWAITING_PAYMENT', 'AWAITING_PAYMENT', 'PAID');

    renderWithProviders(`/orders/${NUMBER}`);

    expect(await screen.findByText('Trwa weryfikacja płatności')).toBeInTheDocument();
    await advance(2_000);
    expect(backend.count()).toBe(2);
    await advance(2_000);

    expect(await screen.findByText('Dziękujemy! Zamówienie opłacone')).toBeInTheDocument();
    await advance(10_000);
    expect(backend.count()).toBe(3);
  });

  it('refreshes the cart counter once the order is paid', async () => {
    serveOrder('PAID');
    let cartRequests = 0;
    server.use(
      http.get(apiUrl('/api/cart'), () => {
        cartRequests += 1;
        return HttpResponse.json({
          lines: [],
          itemCount: 0,
          totalMinor: 0,
          canPlaceOrder: false,
          messages: [],
        });
      }),
    );

    renderWithProviders(`/orders/${NUMBER}`);

    expect(await screen.findByText('Dziękujemy! Zamówienie opłacone')).toBeInTheDocument();
    await vi.waitFor(() => expect(cartRequests).toBeGreaterThanOrEqual(2));
  });

  it('stops waiting after 60 s and asks to refresh later', async () => {
    const backend = serveOrder('AWAITING_PAYMENT');

    renderWithProviders(`/orders/${NUMBER}`);

    expect(await screen.findByText('Trwa weryfikacja płatności')).toBeInTheDocument();
    await advance(60_000);

    expect(
      await screen.findByText('Weryfikacja trwa dłużej niż zwykle — odśwież stronę później.'),
    ).toBeInTheDocument();
    const requestsAtTimeout = backend.count();
    await advance(10_000);
    expect(backend.count()).toBe(requestsAtTimeout);
  });

  it('shows a failed payment with a link to the cart', async () => {
    serveOrder('PAYMENT_FAILED');

    renderWithProviders(`/orders/${NUMBER}`);

    expect(await screen.findByText('Płatność nie powiodła się')).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: 'Wróć do koszyka i spróbuj ponownie' }),
    ).toHaveAttribute('href', '/cart');
  });

  it('tells the customer an order in review has been paid', async () => {
    serveOrder('NEEDS_REVIEW');

    renderWithProviders(`/orders/${NUMBER}`);

    expect(
      await screen.findByText(
        'Płatność otrzymana — skontaktujemy się w sprawie realizacji zamówienia.',
      ),
    ).toBeInTheDocument();
  });

  it('shows "order not found" for an unknown or foreign order', async () => {
    server.use(
      http.get(apiUrl(`/api/orders/${NUMBER}`), () =>
        problem(404, { type: 'about:blank', code: 'NOT_FOUND' }),
      ),
    );

    renderWithProviders(`/orders/${NUMBER}`);

    expect(
      await screen.findByRole('heading', { name: 'Nie znaleziono zamówienia' }),
    ).toBeInTheDocument();
  });
});
