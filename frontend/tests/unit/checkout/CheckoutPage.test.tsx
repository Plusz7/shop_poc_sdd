import { screen, waitFor, within } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Schemas } from '../../../src/api/client';
import { redirectTo } from '../../../src/shared/redirectTo';
import { apiUrl, problem } from '../msw/handlers';
import { server } from '../msw/server';
import { renderWithProviders } from '../renderWithProviders';

vi.mock('../../../src/shared/redirectTo', () => ({ redirectTo: vi.fn() }));

type Cart = Schemas['Cart'];

const cart: Cart = {
  lines: [
    {
      productId: 1,
      name: 'Kubek ceramiczny Łódź',
      imageUrl: '/images/1.svg',
      unitPriceMinor: 4990,
      quantity: 2,
      lineTotalMinor: 9980,
      status: 'AVAILABLE',
      maxQuantity: 10,
      priceChanged: false,
      previousPriceMinor: null,
      quantityExceedsStock: false,
    },
  ],
  itemCount: 2,
  totalMinor: 9980,
  canPlaceOrder: true,
  messages: [],
};

const repricedCart: Cart = {
  ...cart,
  lines: [{ ...cart.lines[0], unitPriceMinor: 5990, lineTotalMinor: 11980, priceChanged: true }],
  totalMinor: 11980,
};

function serveCart(current: Cart) {
  server.use(http.get(apiUrl('/api/cart'), () => HttpResponse.json(current)));
}

/** Records the order requests and answers with `respond`. */
function serveOrders(respond: () => Response) {
  const requests: unknown[] = [];
  server.use(
    http.post(apiUrl('/api/orders'), async ({ request }) => {
      requests.push(await request.json());
      return respond();
    }),
  );
  return requests;
}

async function fillValidForm(user: UserEvent) {
  await user.type(await screen.findByLabelText('Adres e-mail'), 'jan@example.com');
  await user.type(screen.getByLabelText('Imię i nazwisko'), 'Jan Kowalski');
  await user.type(screen.getByLabelText('Ulica i numer'), 'ul. Długa 5');
  await user.type(screen.getByLabelText('Kod pocztowy'), '80-001');
  await user.type(screen.getByLabelText('Miejscowość'), 'Gdańsk');
}

const submit = () => screen.getByRole('button', { name: 'Zamawiam i płacę' });

describe('checkout page', () => {
  beforeEach(() => {
    vi.mocked(redirectTo).mockReset();
    serveCart(cart);
  });

  it('shows the summary priced by the backend and Poland as the only country', async () => {
    renderWithProviders('/checkout');

    const summary = await screen.findByRole('complementary', { name: 'Podsumowanie zamówienia' });
    expect(within(summary).getByText('Kubek ceramiczny Łódź')).toBeInTheDocument();
    expect(within(summary).getByTestId('checkout-total')).toHaveTextContent(/99,80\s*zł/);
    expect(screen.getByText('Polska')).toBeInTheDocument();
  });

  it('shows validation errors next to the fields and sends nothing', async () => {
    const requests = serveOrders(() => HttpResponse.json({}, { status: 201 }));
    const user = userEvent.setup();
    renderWithProviders('/checkout');

    await user.type(await screen.findByLabelText('Kod pocztowy'), '12345');
    await user.click(submit());

    const email = screen.getByLabelText('Adres e-mail');
    const postalCode = screen.getByLabelText('Kod pocztowy');
    expect(email).toHaveAttribute('aria-invalid', 'true');
    expect(email).toHaveAccessibleDescription('Podaj poprawny adres e-mail.');
    expect(postalCode).toHaveAccessibleDescription(
      'Format NN-NNN, np. 00-001 Kod pocztowy musi mieć format NN-NNN.',
    );
    expect(requests).toEqual([]);
  });

  it('sends the details with the confirmed summary and redirects to the payment page', async () => {
    const requests = serveOrders(() =>
      HttpResponse.json(
        { number: 'ORD-7K2Q9M4XTB', paymentUrl: 'https://checkout.stripe.com/c/pay/cs_test_1' },
        { status: 201 },
      ),
    );
    const user = userEvent.setup();
    renderWithProviders('/checkout');

    await fillValidForm(user);
    await user.click(submit());

    await waitFor(() =>
      expect(redirectTo).toHaveBeenCalledWith('https://checkout.stripe.com/c/pay/cs_test_1'),
    );
    expect(requests).toEqual([
      {
        email: 'jan@example.com',
        fullName: 'Jan Kowalski',
        streetAndNumber: 'ul. Długa 5',
        postalCode: '80-001',
        city: 'Gdańsk',
        confirmedSummary: {
          lines: [{ productId: 1, quantity: 2, unitPriceMinor: 4990 }],
          totalMinor: 9980,
        },
      },
    ]);
  });

  it('shows server-side field errors next to the fields', async () => {
    serveOrders(() =>
      problem(400, {
        type: 'about:blank',
        code: 'VALIDATION_ERROR',
        errors: [{ field: 'postalCode', message: 'Kod pocztowy musi mieć format NN-NNN.' }],
      }),
    );
    const user = userEvent.setup();
    renderWithProviders('/checkout');

    await fillValidForm(user);
    await user.click(submit());

    const postalCode = screen.getByLabelText('Kod pocztowy');
    await waitFor(() => expect(postalCode).toHaveAttribute('aria-invalid', 'true'));
    expect(postalCode).toHaveAccessibleDescription(/Kod pocztowy musi mieć format NN-NNN\./);
    expect(redirectTo).not.toHaveBeenCalled();
  });

  it('replaces the summary with the current cart when it is outdated, then confirms again', async () => {
    const requests = serveOrders(() =>
      problem(409, { type: 'about:blank', code: 'SUMMARY_OUTDATED', cart: repricedCart }),
    );
    const user = userEvent.setup();
    renderWithProviders('/checkout');

    await fillValidForm(user);
    await user.click(submit());

    expect(
      await screen.findByText(
        'Ceny lub dostępność produktów zmieniły się — sprawdź podsumowanie i potwierdź zamówienie ponownie.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByTestId('checkout-total')).toHaveTextContent(/119,80\s*zł/);
    expect(redirectTo).not.toHaveBeenCalled();

    serveOrders(() =>
      HttpResponse.json(
        { number: 'ORD-7K2Q9M4XTB', paymentUrl: 'https://checkout.stripe.com/c/pay/cs_test_2' },
        { status: 201 },
      ),
    );
    await user.click(submit());

    await waitFor(() => expect(redirectTo).toHaveBeenCalled());
    expect(requests).toHaveLength(1);
  });

  it('explains that the payment is temporarily unavailable', async () => {
    serveOrders(() => problem(503, { type: 'about:blank', code: 'PAYMENT_UNAVAILABLE' }));
    const user = userEvent.setup();
    renderWithProviders('/checkout');

    await fillValidForm(user);
    await user.click(submit());

    expect(
      await screen.findByText('Płatność jest chwilowo niedostępna, spróbuj ponownie za chwilę.'),
    ).toBeInTheDocument();
    expect(submit()).toBeEnabled();
  });

  it('sends the customer back to the cart when it cannot be ordered', async () => {
    serveCart({ ...cart, canPlaceOrder: false });

    const { router } = renderWithProviders('/checkout');

    await waitFor(() => expect(router.state.location.pathname).toBe('/cart'));
  });
});
