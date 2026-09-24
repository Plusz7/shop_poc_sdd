import { Link } from 'react-router';
import type { Schemas } from '../../api/client';
import { formatPln } from '../../shared/formatPln';
import { AddToCartButton } from '../cart/AddToCartButton';
import { AvailabilityLabel } from './AvailabilityLabel';
import styles from './ProductTile.module.css';

type ProductTileProps = { product: Schemas['ProductSummary'] };

export function ProductTile({ product }: ProductTileProps) {
  return (
    <article className={styles.tile}>
      <Link to={`/product/${product.id}`} className={styles.link}>
        <div className={styles.imageBox}>
          <img
            src={product.image.url}
            alt={product.image.alt}
            loading="lazy"
            width={400}
            height={400}
            className={styles.image}
          />
        </div>
        <h2 className={styles.name}>{product.name}</h2>
      </Link>
      <div className={styles.footer}>
        <span className={styles.price}>{formatPln(product.priceMinor)}</span>
        <AvailabilityLabel status={product.status} />
        <AddToCartButton
          productId={product.id}
          productName={product.name}
          status={product.status}
          maxAddable={product.maxAddable}
          variant="tile"
        />
      </div>
    </article>
  );
}
