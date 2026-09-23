import { useId, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { t } from '../../i18n/t';
import { useCategories } from './api';
import styles from './CatalogPage.module.css';
import {
  minorToPln,
  plnToMinor,
  SORT_OPTIONS,
  type CatalogFilters,
  type FilterPatch,
  type SortOption,
} from './useUrlFilters';

type FiltersProps = {
  filters: CatalogFilters;
  update: (patch: FilterPatch) => void;
  searchFor: (patch: FilterPatch) => string;
};

/** Category navigation, price range (PLN) and sorting of the product list. */
export function Filters({ filters, update, searchFor }: FiltersProps) {
  return (
    <div className={styles.filters}>
      <CategoryNav selected={filters.category} searchFor={searchFor} />
      <div className={styles.filterRow}>
        <PriceRange
          key={`${filters.minPrice}-${filters.maxPrice}`}
          minPrice={filters.minPrice}
          maxPrice={filters.maxPrice}
          onApply={(minPrice, maxPrice) => update({ minPrice, maxPrice })}
        />
        <SortSelect value={filters.sort} onChange={(sort) => update({ sort })} />
      </div>
    </div>
  );
}

function CategoryNav({
  selected,
  searchFor,
}: {
  selected?: string;
  searchFor: FiltersProps['searchFor'];
}) {
  const { data: categories = [] } = useCategories();
  const entries = [{ slug: undefined, name: t('catalog.allCategories') }, ...categories];
  return (
    <nav aria-label={t('catalog.categories')}>
      <ul className={styles.categories}>
        {entries.map((category) => {
          const current = category.slug === selected;
          return (
            <li key={category.slug ?? ''}>
              <Link
                to={{ pathname: '/', search: searchFor({ category: category.slug }) }}
                aria-current={current ? 'page' : undefined}
                className={
                  current ? `${styles.category} ${styles.categoryCurrent}` : styles.category
                }
              >
                {category.name}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

type PriceRangeProps = {
  minPrice?: number;
  maxPrice?: number;
  onApply: (minPrice: number | undefined, maxPrice: number | undefined) => void;
};

function PriceRange({ minPrice, maxPrice, onApply }: PriceRangeProps) {
  const id = useId();
  const [min, setMin] = useState(minPrice === undefined ? '' : minorToPln(minPrice));
  const [max, setMax] = useState(maxPrice === undefined ? '' : minorToPln(maxPrice));
  const [invalid, setInvalid] = useState(false);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const minMinor = min.trim() ? plnToMinor(min) : undefined;
    const maxMinor = max.trim() ? plnToMinor(max) : undefined;
    const hasError =
      (min.trim() !== '' && minMinor === undefined) ||
      (max.trim() !== '' && maxMinor === undefined);
    setInvalid(hasError);
    if (!hasError) {
      onApply(minMinor, maxMinor);
    }
  };

  const errorId = `${id}-error`;
  return (
    <form className={styles.priceRange} onSubmit={submit} noValidate>
      <label>
        {t('catalog.priceFrom')}
        <input
          value={min}
          onChange={(event) => setMin(event.target.value)}
          inputMode="decimal"
          aria-invalid={invalid || undefined}
          aria-describedby={invalid ? errorId : undefined}
        />
      </label>
      <label>
        {t('catalog.priceTo')}
        <input
          value={max}
          onChange={(event) => setMax(event.target.value)}
          inputMode="decimal"
          aria-invalid={invalid || undefined}
          aria-describedby={invalid ? errorId : undefined}
        />
      </label>
      <button type="submit">{t('catalog.applyPrice')}</button>
      {invalid && (
        <p id={errorId} className={styles.fieldError}>
          {t('catalog.priceInvalid')}
        </p>
      )}
    </form>
  );
}

function SortSelect({
  value,
  onChange,
}: {
  value: SortOption;
  onChange: (sort: SortOption) => void;
}) {
  return (
    <label className={styles.sort}>
      {t('catalog.sort')}
      <select value={value} onChange={(event) => onChange(event.target.value as SortOption)}>
        {SORT_OPTIONS.map((option) => (
          <option key={option} value={option}>
            {t(`catalog.sort.${option}`)}
          </option>
        ))}
      </select>
    </label>
  );
}
