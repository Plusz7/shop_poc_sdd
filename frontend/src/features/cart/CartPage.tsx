import { Link } from 'react-router';
import type { Schemas } from '../../api/client';
import { t } from '../../i18n/t';
import { Banner } from '../../shared/Banner';
import { formatPln } from '../../shared/formatPln';
import styles from './CartPage.module.css';
import { useCart } from './useCart';

/** Cart `/cart`: lines priced by the API and the total (US2; editing comes with US3). */
export function CartPage() {
  const { data: cart, isPending, isError, refetch } = useCart();

  if (isPending) {
    return <p role="status">{t('common.loading')}</p>;
  }
  if (isError) {
    return (
      <Banner
        kind="error"
        action={
          <button type="button" onClick={() => refetch()}>
            {t('common.retry')}
          </button>
        }
      >
        {t('common.error')}
      </Banner>
    );
  }

  return (
    <section className={styles.cart}>
      <h1>{t('cart.title')}</h1>
      {cart.lines.length === 0 ? (
        <div className={styles.empty}>
          <p>{t('cart.empty')}</p>
          <Link to="/">{t('cart.backToShop')}</Link>
        </div>
      ) : (
        <>
          <ul className={styles.lines} aria-label={t('cart.lines')}>
            {cart.lines.map((line) => (
              <CartLine key={line.productId} line={line} />
            ))}
          </ul>
          <p className={styles.total}>
            <span>{t('cart.total')}</span>
            <strong>{formatPln(cart.totalMinor)}</strong>
          </p>
        </>
      )}
    </section>
  );
}

function CartLine({ line }: { line: Schemas['CartLine'] }) {
  return (
    <li className={styles.line}>
      <img
        src={line.imageUrl}
        alt=""
        width={80}
        height={80}
        loading="lazy"
        className={styles.image}
      />
      <div className={styles.details}>
        <Link to={`/product/${line.productId}`} className={styles.name}>
          {line.name}
        </Link>
        <span>{t('cart.unitPrice', { price: formatPln(line.unitPriceMinor) })}</span>
        <span>{t('cart.lineQuantity', { quantity: line.quantity })}</span>
      </div>
      <strong className={styles.lineTotal}>{formatPln(line.lineTotalMinor)}</strong>
    </li>
  );
}
