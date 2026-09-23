import type { ReactNode } from 'react';
import styles from './Banner.module.css';

type BannerProps = {
  kind?: 'info' | 'warning' | 'error';
  children: ReactNode;
  action?: ReactNode;
};

/** Inline message on a page; errors are announced assertively, other kinds politely. */
export function Banner({ kind = 'info', children, action }: BannerProps) {
  return (
    <div role={kind === 'error' ? 'alert' : 'status'} className={`${styles.banner} ${styles[kind]}`}>
      <div className={styles.text}>{children}</div>
      {action && <div className={styles.action}>{action}</div>}
    </div>
  );
}
