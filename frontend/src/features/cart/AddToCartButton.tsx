import { useId, useState } from 'react';
import type { Schemas } from '../../api/client';
import { t } from '../../i18n/t';
import styles from './AddToCartButton.module.css';
import { useAddToCart } from './useCart';

type AddToCartButtonProps = {
  productId: number;
  productName: string;
  status: Schemas['AvailabilityStatus'];
  /** How many items one cart line may hold, from the API (`maxAddable`). */
  maxAddable: number;
  /** `product` - with quantity selection (product page); `tile` - adds one item (product list). */
  variant: 'product' | 'tile';
};

/**
 * "Add to cart" (US2). Availability and limits come only from the API; an unavailable product keeps a
 * focusable button with `aria-disabled` and an explanation instead of a silently disabled one.
 */
export function AddToCartButton({
  productId,
  productName,
  status,
  maxAddable,
  variant,
}: AddToCartButtonProps) {
  const addToCart = useAddToCart();
  const [quantity, setQuantity] = useState(1);
  const quantityId = useId();
  const hintId = useId();
  const unavailable = status === 'UNAVAILABLE' || maxAddable < 1;

  const add = () => {
    if (unavailable || addToCart.isPending) {
      return;
    }
    addToCart.mutate({ productId, quantity: variant === 'product' ? quantity : 1 });
  };

  return (
    <div className={variant === 'product' ? styles.product : styles.tile}>
      {variant === 'product' && !unavailable && (
        <div className={styles.quantity}>
          <label htmlFor={quantityId}>{t('cart.quantity')}</label>
          <select
            id={quantityId}
            value={quantity}
            onChange={(event) => setQuantity(Number(event.target.value))}
          >
            {Array.from({ length: maxAddable }, (_, index) => index + 1).map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </div>
      )}
      <button
        type="button"
        className={styles.button}
        onClick={add}
        aria-label={variant === 'tile' ? t('cart.addNamed', { name: productName }) : undefined}
        aria-disabled={unavailable || addToCart.isPending ? true : undefined}
        aria-describedby={unavailable ? hintId : undefined}
        aria-busy={addToCart.isPending || undefined}
      >
        {t('cart.add')}
      </button>
      {unavailable && (
        <p id={hintId} className={variant === 'product' ? styles.hint : styles.visuallyHidden}>
          {t('cart.productUnavailable')}
        </p>
      )}
    </div>
  );
}
