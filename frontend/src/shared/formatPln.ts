const formatter = new Intl.NumberFormat('pl-PL', { style: 'currency', currency: 'PLN' });

/**
 * Formats an amount in grosze (minor units, as returned by the API) for display, e.g. 123456 → "1234,56 zł".
 * Presentation only - the frontend never calculates prices.
 */
export function formatPln(minor: number): string {
  return formatter.format(minor / 100);
}
