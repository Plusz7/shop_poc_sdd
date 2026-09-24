import { Link, Outlet } from 'react-router';
import { CartCounter } from '../features/cart/CartCounter';
import { t } from '../i18n/t';
import styles from './Layout.module.css';
import { SearchBox } from './SearchBox';

/** Page shell: header (logo, search box, cart counter) and the routed content. */
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
          <div className={styles.search}>
            <SearchBox />
          </div>
          <div className={styles.cart}>
            <CartCounter />
          </div>
        </div>
      </header>
      <main id="main" className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}
