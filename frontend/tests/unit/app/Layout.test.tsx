import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../renderWithProviders';

describe('application shell', () => {
  it('renders the header with a link to the shop', () => {
    renderWithProviders('/');

    expect(screen.getByRole('link', { name: 'Strona główna sklepu' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('main')).toBeInTheDocument();
  });

  it('shows the not found page for an unknown route', () => {
    renderWithProviders('/no-such-page');

    expect(screen.getByRole('heading', { name: 'Nie znaleziono strony' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Wróć do sklepu' })).toHaveAttribute('href', '/');
  });
});
