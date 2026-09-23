import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { t } from '../i18n/t';
import styles from './Layout.module.css';

/** Header search: submitted with Enter or the button, opens `/?q=…` from page 1 (US1-3). */
export function SearchBox() {
  const [searchParams] = useSearchParams();
  const { pathname } = useLocation();
  const currentQuery = pathname === '/' ? (searchParams.get('q') ?? '') : '';
  // the field follows the URL when it changes elsewhere (back button, "Clear filters")
  return <SearchForm key={currentQuery} initialQuery={currentQuery} />;
}

function SearchForm({ initialQuery }: { initialQuery: string }) {
  const navigate = useNavigate();
  const [query, setQuery] = useState(initialQuery);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const phrase = query.trim();
    navigate(phrase ? { pathname: '/', search: `?${new URLSearchParams({ q: phrase })}` } : '/');
  };

  return (
    <form role="search" className={styles.searchForm} onSubmit={submit}>
      <input
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        aria-label={t('search.label')}
        placeholder={t('search.placeholder')}
        maxLength={100}
        className={styles.searchInput}
      />
      <button type="submit" className={styles.searchButton}>
        {t('search.submit')}
      </button>
    </form>
  );
}
