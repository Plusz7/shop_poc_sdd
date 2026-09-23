import { Link } from 'react-router';
import { t } from '../i18n/t';

export function NotFoundPage() {
  return (
    <section>
      <h1>{t('notFound.title')}</h1>
      <p>{t('notFound.text')}</p>
      <Link to="/">{t('notFound.backToShop')}</Link>
    </section>
  );
}
