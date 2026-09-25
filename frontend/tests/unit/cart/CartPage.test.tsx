import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Schemas } from '../../../src/api/client';
import { apiUrl, emptyCart, problem } from '../msw/handlers';
import { server } from '../msw/server';
import { renderWithProviders } from '../renderWithProviders';

type CartLine = Schemas['CartLine'];
type Cart = Schemas['Cart'];

function line(overrides: Partial<CartLine> & Pick<CartLine, 'productId' | 'name'>): CartLine {
  const unitPriceMinor = overrides.unitPriceMinor ?? 1000;
  const quantity = overrides.quantity ?? 1;
  return {
    imageUrl: `/images/${overrides.productId}.svg`,
    unitPriceMinor,
    quantity,
    lineTotalMinor: unitPriceMinor * quantity,
    status: 'AVAILABLE',
    maxQuantity: 10,
    priceChanged: false,
    previousPriceMinor: null,
    quantityExceedsStock: false,
    ...overrides,
  };
}

function cartOf(lines: CartLine[], overrides: Partial<Cart> = {}): Cart {
  return {
    lines,
    itemCount: lines.reduce((sum, cartLine) => sum + cartLine.quantity, 0),
    totalMinor: lines
      .filter((cartLine) => cartLine.status !== 'UNAVAILABLE')
      .reduce((sum, cartLine) => sum + cartLine.lineTotalMinor, 0),
    canPlaceOrder: lines.length > 0,
    messages: [],
    ...overrides,
  };
}

const mug = line({
  productId: 1,
  name: 'Kubek ceramiczny Łódź',
  unitPriceMinor: 4990,
  quantity: 1,
});
const lamp = line({
  productId: 2,
  name: 'Lampka biurkowa LED Duo',
  unitPriceMinor: 12900,
  quantity: 2,
});

/** Serves `cart` for GET /api/cart and records every cart mutation request. */
function serveCart(cart: Cart) {
  const requests: { method: string; path: string; body?: unknown }[] = [];
  let current = cart;
  const record = async (request: Request) => {
    const text = await request.text();
    requests.push({
      method: request.method,
      path: new URL(request.url).pathname,
      body: text ? JSON.parse(text) : undefined,
    });
  };
  server.use(http.get(apiUrl('/api/cart'), () => HttpResponse.json(current)));
  return {
    requests,
    respondWith(method: 'put' | 'delete' | 'post', path: string, next: Cart | (() => Response)) {
      server.use(
        http[method](apiUrl(path), async ({ request }) => {
          await record(request);
          if (typeof next === 'function') {
            return next();
          }
          current = next;
          return HttpResponse.json(next);
        }),
      );
    },
  };
}

describe('cart page', () => {
  it('shows that the payment was not completed after returning from Stripe', async () => {
    serveCart(cartOf([mug]));

    renderWithProviders('/cart?payment=canceled');

    expect(
      await screen.findByText('Płatność nie została dokończona. Twój koszyk czeka.'),
    ).toBeInTheDocument();
    expect(await findCartLines()).toHaveLength(1);
  });

  it('shows the empty state with a link to the shop and no checkout button', async () => {
    serveCart(emptyCart);

    renderWithProviders('/cart');

    expect(await screen.findByText('Twój koszyk jest pusty')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Przejdź do sklepu' })).toHaveAttribute('href', '/');
    expect(screen.queryByRole('link', { name: 'Przejdź do kasy' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Przejdź do kasy' })).not.toBeInTheDocument();
  });

  it('shows the lines, the total from the API and a link to the checkout', async () => {
    serveCart(cartOf([mug, lamp]));

    renderWithProviders('/cart');

    const lines = await findCartLines();
    expect(lines).toHaveLength(2);
    expect(
      within(lines[1]).getByRole('textbox', { name: 'Liczba sztuk: Lampka biurkowa LED Duo' }),
    ).toHaveValue('2');
    expect(within(lines[1]).getByText(/258,00\s*zł/)).toBeInTheDocument();
    expect(screen.getByText(/307,90\s*zł/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Przejdź do kasy' })).toHaveAttribute(
      'href',
      '/checkout',
    );
  });

  it('changes the quantity and shows the recalculated cart', async () => {
    const backend = serveCart(cartOf([mug]));
    backend.respondWith(
      'put',
      '/api/cart/lines/1',
      cartOf([{ ...mug, quantity: 3, lineTotalMinor: 14970 }]),
    );
    const user = userEvent.setup();

    renderWithProviders('/cart');
    const quantity = await screen.findByRole('textbox', {
      name: 'Liczba sztuk: Kubek ceramiczny Łódź',
    });
    await user.clear(quantity);
    await user.type(quantity, '3{Enter}');

    await waitFor(() => expect(screen.getAllByText(/149,70\s*zł/).length).toBeGreaterThan(0));
    expect(backend.requests).toEqual([
      { method: 'PUT', path: '/api/cart/lines/1', body: { quantity: 3 } },
    ]);
    expect(
      await screen.findByRole('link', { name: 'Koszyk, liczba produktów: 3' }),
    ).toBeInTheDocument();
  });

  it('caps a quantity above the stock and tells the customer how many are available', async () => {
    const backend = serveCart(cartOf([mug]));
    backend.respondWith(
      'put',
      '/api/cart/lines/1',
      cartOf([{ ...mug, quantity: 5, lineTotalMinor: 24950, maxQuantity: 5 }], {
        messages: [{ code: 'QUANTITY_CAPPED', productId: 1, text: 'Dostępnych jest tylko 5 szt.' }],
      }),
    );
    const user = userEvent.setup();

    renderWithProviders('/cart');
    const quantity = await screen.findByRole('textbox', {
      name: 'Liczba sztuk: Kubek ceramiczny Łódź',
    });
    await user.clear(quantity);
    await user.type(quantity, '50');
    await user.tab();

    expect(await screen.findByText('Dostępnych jest tylko 5 szt.')).toBeInTheDocument();
    await waitFor(() =>
      expect(
        screen.getByRole('textbox', { name: 'Liczba sztuk: Kubek ceramiczny Łódź' }),
      ).toHaveValue('5'),
    );
  });

  it.each(['1.5', '-1', 'abc', '100'])(
    'rejects the quantity "%s" without calling the API',
    async (value) => {
      const backend = serveCart(cartOf([mug]));
      backend.respondWith('put', '/api/cart/lines/1', cartOf([mug]));
      const user = userEvent.setup();

      renderWithProviders('/cart');
      const quantity = await screen.findByRole('textbox', {
        name: 'Liczba sztuk: Kubek ceramiczny Łódź',
      });
      await user.clear(quantity);
      await user.type(quantity, `${value}{Enter}`);

      expect(await screen.findByText('Podaj liczbę sztuk od 0 do 99.')).toBeInTheDocument();
      expect(quantity).toHaveAttribute('aria-invalid', 'true');
      expect(backend.requests).toEqual([]);
    },
  );

  it('removes a line', async () => {
    const backend = serveCart(cartOf([mug, lamp]));
    backend.respondWith('delete', '/api/cart/lines/1', cartOf([lamp]));
    const user = userEvent.setup();

    renderWithProviders('/cart');
    await user.click(await screen.findByRole('button', { name: 'Usuń: Kubek ceramiczny Łódź' }));

    await waitFor(() => expect(cartLines()).toHaveLength(1));
    expect(screen.queryByText('Kubek ceramiczny Łódź')).not.toBeInTheDocument();
    expect(backend.requests).toEqual([
      { method: 'DELETE', path: '/api/cart/lines/1', body: undefined },
    ]);
  });

  it('clears the cart', async () => {
    const backend = serveCart(cartOf([mug, lamp]));
    backend.respondWith('delete', '/api/cart', emptyCart);
    const user = userEvent.setup();

    renderWithProviders('/cart');
    await user.click(await screen.findByRole('button', { name: 'Wyczyść koszyk' }));

    expect(await screen.findByText('Twój koszyk jest pusty')).toBeInTheDocument();
    expect(backend.requests).toEqual([{ method: 'DELETE', path: '/api/cart', body: undefined }]);
  });

  it('shows a changed price with the previous one struck through until the customer accepts it', async () => {
    const changed = {
      ...mug,
      unitPriceMinor: 5490,
      lineTotalMinor: 5490,
      priceChanged: true,
      previousPriceMinor: 4990,
    };
    const backend = serveCart(
      cartOf([changed], {
        messages: [
          {
            code: 'PRICE_CHANGED',
            productId: 1,
            text: 'Cena produktu „Kubek ceramiczny Łódź” zmieniła się.',
          },
        ],
      }),
    );
    backend.respondWith(
      'post',
      '/api/cart/accept-prices',
      cartOf([{ ...changed, priceChanged: false, previousPriceMinor: null }]),
    );
    const user = userEvent.setup();

    renderWithProviders('/cart');

    const banner = await screen.findByText(
      'Ceny niektórych produktów zmieniły się od dodania ich do koszyka.',
    );
    const previousPrice = screen.getByText(/49,90\s*zł/);
    expect(previousPrice.closest('del')).not.toBeNull();
    expect(screen.getAllByText(/54,90\s*zł/).length).toBeGreaterThan(0);
    expect(banner).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Rozumiem' }));

    await waitFor(() =>
      expect(
        screen.queryByText('Ceny niektórych produktów zmieniły się od dodania ich do koszyka.'),
      ).not.toBeInTheDocument(),
    );
    expect(screen.queryByText(/49,90\s*zł/)).not.toBeInTheDocument();
    expect(backend.requests).toEqual([
      { method: 'POST', path: '/api/cart/accept-prices', body: undefined },
    ]);
  });

  it('greys out an unavailable line, offers only removal and blocks the checkout with a hint', async () => {
    const soldOut = { ...lamp, status: 'UNAVAILABLE' as const, maxQuantity: 0 };
    serveCart(
      cartOf([mug, soldOut], {
        canPlaceOrder: false,
        messages: [
          {
            code: 'PRODUCT_UNAVAILABLE',
            productId: 2,
            text: 'Produkt „Lampka biurkowa LED Duo” jest niedostępny. Usuń go z koszyka, aby złożyć zamówienie.',
          },
        ],
      }),
    );

    renderWithProviders('/cart');

    const lines = await findCartLines();
    const unavailable = lines[1];
    expect(within(unavailable).getByText('Niedostępny')).toBeInTheDocument();
    expect(within(unavailable).queryByRole('textbox')).not.toBeInTheDocument();
    expect(
      within(unavailable).getByRole('button', { name: 'Usuń: Lampka biurkowa LED Duo' }),
    ).toBeEnabled();

    const checkout = screen.getByRole('button', { name: 'Przejdź do kasy' });
    expect(checkout).toBeDisabled();
    expect(checkout).toHaveAccessibleDescription(
      'Produkt „Lampka biurkowa LED Duo” jest niedostępny. Usuń go z koszyka, aby złożyć zamówienie.',
    );
    expect(screen.queryByRole('link', { name: 'Przejdź do kasy' })).not.toBeInTheDocument();
  });

  it('marks a line whose quantity exceeds the current stock', async () => {
    serveCart(
      cartOf(
        [
          {
            ...mug,
            quantity: 4,
            lineTotalMinor: 19960,
            maxQuantity: 2,
            quantityExceedsStock: true,
          },
        ],
        {
          canPlaceOrder: false,
          messages: [
            {
              code: 'QUANTITY_EXCEEDS_STOCK',
              productId: 1,
              text: 'Produktu „Kubek ceramiczny Łódź” dostępnych jest tylko 2 szt. Zmniejsz liczbę sztuk, aby złożyć zamówienie.',
            },
          ],
        },
      ),
    );

    renderWithProviders('/cart');

    expect(await screen.findByText('Dostępnych: 2 szt.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Przejdź do kasy' })).toBeDisabled();
  });

  it('explains a change made in another tab and reloads the cart', async () => {
    const backend = serveCart(cartOf([mug]));
    backend.respondWith('delete', '/api/cart/lines/1', () =>
      problem(409, {
        type: 'about:blank',
        code: 'CONCURRENCY_CONFLICT',
        detail: 'Koszyk został zmieniony w innym oknie.',
      }),
    );
    const user = userEvent.setup();

    renderWithProviders('/cart');
    await user.click(await screen.findByRole('button', { name: 'Usuń: Kubek ceramiczny Łódź' }));

    expect(
      await screen.findByText(
        'Koszyk został zmieniony w innym oknie. Sprawdź go i spróbuj ponownie.',
      ),
    ).toBeInTheDocument();
  });
});

function cartLines() {
  return within(screen.getByRole('list', { name: 'Produkty w koszyku' })).getAllByRole('listitem');
}

async function findCartLines() {
  return within(await screen.findByRole('list', { name: 'Produkty w koszyku' })).findAllByRole(
    'listitem',
  );
}
