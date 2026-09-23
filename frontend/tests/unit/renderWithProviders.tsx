import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from '../../src/app/routes';
import { ToastProvider } from '../../src/shared/toast/ToastProvider';

/**
 * Renders the application routes at `url` with a fresh QueryClient (no retries) and the toast system,
 * like main.tsx does, but with an in-memory router.
 */
export function renderWithProviders(url = '/') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(routes, { initialEntries: [url] });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return { ...result, router, queryClient };
}
