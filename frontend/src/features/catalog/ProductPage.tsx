import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError, type Schemas } from '../../api/client';
import { t } from '../../i18n/t';
import { AddToCartButton } from '../cart/AddToCartButton';
import { Banner } from '../../shared/Banner';
import { formatPln } from '../../shared/formatPln';
import { useProduct } from './api';
import { AvailabilityLabel } from './AvailabilityLabel';
import styles from './ProductPage.module.css';

/** Product page `/product/:id` (US1-6, FR-005). */
export function ProductPage() {
  const { id } = useParams();
  const productId = id && /^\d{1,15}$/.test(id) && Number(id) > 0 ? Number(id) : undefined;
  const { data: product, isPending, isError, error, refetch } = useProduct(productId);

  if (productId === undefined || (error instanceof ApiError && error.status === 404)) {
    return <ProductNotFound />;
  }
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
    <article className={styles.product}>
      <Gallery key={product.id} images={product.images} />
      <div className={styles.details}>
        <Link
          to={{ pathname: '/', search: `?category=${product.category.slug}` }}
          className={styles.category}
        >
          {product.category.name}
        </Link>
        <h1 className={styles.name}>{product.name}</h1>
        <p className={styles.price}>{formatPln(product.priceMinor)}</p>
        <AvailabilityLabel status={product.status} />
        <AddToCartButton
          key={product.id}
          productId={product.id}
          productName={product.name}
          status={product.status}
          maxAddable={product.maxAddable}
          variant="product"
        />
        <section className={styles.description}>
          <h2>{t('product.description')}</h2>
          <p>{product.description}</p>
        </section>
      </div>
    </article>
  );
}

function Gallery({ images }: { images: Schemas['Image'][] }) {
  const [selected, setSelected] = useState(0);
  const main = images[selected] ?? images[0];
  return (
    <section className={styles.gallery} aria-label={t('product.gallery')}>
      <div className={styles.mainImage}>
        <img src={main.url} alt={main.alt} width={600} height={600} />
      </div>
      {images.length > 1 && (
        <ul className={styles.thumbnails}>
          {images.map((image, index) => (
            <li key={image.url + index}>
              <button
                type="button"
                onClick={() => setSelected(index)}
                aria-label={t('product.showImage', { index: index + 1 })}
                aria-pressed={index === selected}
                className={styles.thumbnail}
              >
                <img src={image.url} alt="" loading="lazy" width={80} height={80} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function ProductNotFound() {
  return (
    <section>
      <h1>{t('product.notFound')}</h1>
      <p>{t('product.notFoundText')}</p>
      <Link to="/">{t('product.backToList')}</Link>
    </section>
  );
}
