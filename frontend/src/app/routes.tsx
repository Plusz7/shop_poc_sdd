import type { RouteObject } from 'react-router';
import { CartPage } from '../features/cart/CartPage';
import { CatalogPage } from '../features/catalog/CatalogPage';
import { ProductPage } from '../features/catalog/ProductPage';
import { Layout } from './Layout';
import { NotFoundPage } from './NotFoundPage';

/**
 * Route table (contracts/frontend-routes.md). Shared by the browser router and by component tests.
 * Feature pages are registered by their user stories: `/`, `/product/:id` (US1), `/cart` (US2-US3),
 * `/checkout`, `/orders/:number` (US4).
 */
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { index: true, element: <CatalogPage /> },
      { path: 'product/:id', element: <ProductPage /> },
      { path: 'cart', element: <CartPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
