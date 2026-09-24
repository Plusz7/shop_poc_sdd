import { Link } from 'react-router';
import { t } from '../../i18n/t';
import styles from './CartCounter.module.css';
import { useCart } from './useCart';

/** Header link to the cart with the number of items (FR-013). */
export function CartCounter() {
  const { data } = useCart();
  const count = data?.itemCount ?? 0;
  return (
    <Link to="/cart" className={styles.counter} aria-label={t('header.cart', { count })}>
      <span>{t('header.cartLabel')}</span>
      <span className={styles.badge} aria-hidden="true">
        {count}
      </span>
    </Link>
  );
}
