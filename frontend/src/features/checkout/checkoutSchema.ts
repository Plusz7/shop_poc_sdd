import { z } from 'zod';
import { t } from '../../i18n/t';

/**
 * Checkout form (FR-015). UX only - the backend validates the same rules and has the final word; the country
 * is fixed to Poland and not sent at all.
 */
export const checkoutSchema = z.object({
  email: z
    .string()
    .trim()
    .max(254, t('checkout.error.email'))
    .regex(/^[^@\s]+@[^@\s]+\.[^@\s]+$/, t('checkout.error.email')),
  fullName: z
    .string()
    .trim()
    .min(2, t('checkout.error.fullName'))
    .max(100, t('checkout.error.fullName')),
  streetAndNumber: z
    .string()
    .trim()
    .min(3, t('checkout.error.streetAndNumber'))
    .max(120, t('checkout.error.streetAndNumber')),
  postalCode: z
    .string()
    .trim()
    .regex(/^\d{2}-\d{3}$/, t('checkout.error.postalCode')),
  city: z.string().trim().min(2, t('checkout.error.city')).max(60, t('checkout.error.city')),
});

export type CheckoutForm = z.infer<typeof checkoutSchema>;

export const checkoutFields = [
  'email',
  'fullName',
  'streetAndNumber',
  'postalCode',
  'city',
] as const satisfies readonly (keyof CheckoutForm)[];
