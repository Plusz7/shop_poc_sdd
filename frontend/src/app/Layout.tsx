import { Link, Outlet } from 'react-router';
import { t } from '../i18n/t';
import styles from './Layout.module.css';

/** Page shell: header (logo, search box slot, cart counter slot) and the routed content. */
export function Layout() {
  return (
    <div className={styles.page}>
      <a className={styles.skipLink} href="#main">
        {t('app.skipToContent')}
      </a>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to="/" className={styles.logo} aria-label={t('header.home')}>
            {t('app.name')}
          </Link>
          <div className={styles.search} data-slot="search" />
          <div className={styles.cart} data-slot="cart" />
        </div>
      </header>
      <main id="main" className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}
