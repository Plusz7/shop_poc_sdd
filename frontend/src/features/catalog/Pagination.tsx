import { t } from '../../i18n/t';
import styles from './Pagination.module.css';

type PaginationProps = {
  /** 1-based */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
};

export function Pagination({ page, totalPages, onChange }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <nav className={styles.pagination} aria-label={t('pagination.label')}>
      <button type="button" disabled={page <= 1} onClick={() => onChange(page - 1)}>
        {t('pagination.previous')}
      </button>
      <span aria-current="page">{t('pagination.status', { page, total: totalPages })}</span>
      <button type="button" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>
        {t('pagination.next')}
      </button>
    </nav>
  );
}
