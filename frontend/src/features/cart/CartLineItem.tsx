import { useId, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import type { Schemas } from '../../api/client';
import { t } from '../../i18n/t';
import { formatPln } from '../../shared/formatPln';
import styles from './CartLineItem.module.css';
import { useChangeQuantity, useRemoveLine } from './useCart';

type CartLine = Schemas['CartLine'];

/**
 * One cart line (US3): the current price (with the previous one struck through after a price change), an
 * editable quantity and removal. An unavailable line can only be removed (US3-7).
 */
export function CartLineItem({ line }: { line: CartLine }) {
  const removeLine = useRemoveLine();
  const unavailable = line.status === 'UNAVAILABLE';

  return (
    <li className={`${styles.line} ${unavailable ? styles.unavailable : ''}`}>
      <img
        src={line.imageUrl || undefined}
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
        <span>
          {t('cart.unitPrice', { price: formatPln(line.unitPriceMinor) })}
          {line.priceChanged && line.previousPriceMinor != null && (
            <>
              {' '}
              <del className={styles.previousPrice}>
                <span className={styles.visuallyHidden}>{t('cart.previousPrice')} </span>
                {formatPln(line.previousPriceMinor)}
              </del>
            </>
          )}
        </span>
        {unavailable && <span className={styles.status}>{t('availability.UNAVAILABLE')}</span>}
        {line.quantityExceedsStock && (
          <span className={styles.status}>
            {t('cart.availableQuantity', { max: line.maxQuantity })}
          </span>
        )}
      </div>
      <div className={styles.actions}>
        {!unavailable && <QuantityField key={line.quantity} line={line} />}
        <button
          type="button"
          className={styles.remove}
          aria-label={t('cart.removeNamed', { name: line.name })}
          disabled={removeLine.isPending}
          onClick={() => removeLine.mutate(line.productId)}
        >
          {t('cart.remove')}
        </button>
      </div>
      <strong className={styles.lineTotal}>{formatPln(line.lineTotalMinor)}</strong>
    </li>
  );
}

/** An integer 0–99 (0 removes the line); saved on Enter or when the field loses focus. */
function QuantityField({ line }: { line: CartLine }) {
  const [value, setValue] = useState(String(line.quantity));
  const [invalid, setInvalid] = useState(false);
  const changeQuantity = useChangeQuantity();
  const errorId = useId();

  const save = () => {
    const trimmed = value.trim();
    if (!/^\d{1,2}$/.test(trimmed)) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    const quantity = Number(trimmed);
    if (quantity === line.quantity || changeQuantity.isPending) {
      return;
    }
    changeQuantity.mutate(
      { productId: line.productId, quantity },
      {
        onSuccess: (cart) => {
          const saved = cart.lines.find((cartLine) => cartLine.productId === line.productId);
          if (saved) {
            setValue(String(saved.quantity));
          }
        },
      },
    );
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    save();
  };

  return (
    <form className={styles.quantity} onSubmit={onSubmit} noValidate>
      <input
        type="text"
        inputMode="numeric"
        autoComplete="off"
        aria-label={t('cart.quantityFor', { name: line.name })}
        aria-invalid={invalid}
        aria-describedby={invalid ? errorId : undefined}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        onBlur={save}
      />
      {invalid && (
        <p id={errorId} className={styles.error}>
          {t('cart.quantityInvalid')}
        </p>
      )}
    </form>
  );
}
