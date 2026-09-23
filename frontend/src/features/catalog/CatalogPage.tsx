import { t } from '../../i18n/t';
import { Banner } from '../../shared/Banner';
import { useProducts } from './api';
import styles from './CatalogPage.module.css';
import { Filters } from './Filters';
import { Pagination } from './Pagination';
import { ProductTile } from './ProductTile';
import { useUrlFilters } from './useUrlFilters';

/** Product list `/` (US1): filters and page from the URL, results from the API. */
export function CatalogPage() {
  const { filters, apiQuery, update, setPage, clear, searchFor } = useUrlFilters();
  const { data, isPending, isError, refetch } = useProducts(apiQuery);

  return (
    <section>
      <h1 className={styles.title}>
        {filters.q ? t('catalog.searchTitle', { q: filters.q }) : t('catalog.title')}
      </h1>
      <Filters filters={filters} update={update} searchFor={searchFor} />

      {isPending && <p role="status">{t('common.loading')}</p>}

      {isError && (
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
      )}

      {data && data.products.length === 0 && (
        <div className={styles.empty}>
          <h2>{t('catalog.noResults')}</h2>
          <p>{t('catalog.noResultsText')}</p>
          <button type="button" className={styles.primaryButton} onClick={clear}>
            {t('catalog.clearFilters')}
          </button>
        </div>
      )}

      {data && data.products.length > 0 && (
        <>
          <p className={styles.count}>{t('catalog.resultsCount', { count: data.totalElements })}</p>
          <ul className={styles.grid} aria-label={t('catalog.title')}>
            {data.products.map((product) => (
              <li key={product.id}>
                <ProductTile product={product} />
              </li>
            ))}
          </ul>
          <Pagination page={filters.page} totalPages={data.totalPages} onChange={setPage} />
        </>
      )}
    </section>
  );
}
