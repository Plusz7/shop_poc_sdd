import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react';
import { t } from '../../i18n/t';
import { ToastContext, type ToastKind } from './useToast';
import styles from './Toast.module.css';

type Toast = { id: number; message: string; kind: ToastKind };

const AUTO_DISMISS_MS = 5000;

/**
 * Accessible toasts: informational messages in a polite `role="status"` region,
 * errors in an assertive `role="alert"` region.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const show = useCallback(
    (message: string, kind: ToastKind = 'info') => {
      const id = nextId.current++;
      setToasts((current) => [...current, { id, message, kind }]);
      setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
    },
    [dismiss],
  );

  const value = useMemo(() => ({ show }), [show]);
  const infos = toasts.filter((toast) => toast.kind !== 'error');
  const errors = toasts.filter((toast) => toast.kind === 'error');

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className={styles.container}>
        <div role="status" aria-live="polite">
          {infos.map((toast) => (
            <ToastItem key={toast.id} toast={toast} onClose={dismiss} />
          ))}
        </div>
        <div role="alert" aria-live="assertive">
          {errors.map((toast) => (
            <ToastItem key={toast.id} toast={toast} onClose={dismiss} />
          ))}
        </div>
      </div>
    </ToastContext.Provider>
  );
}

function ToastItem({ toast, onClose }: { toast: Toast; onClose: (id: number) => void }) {
  return (
    <div className={`${styles.toast} ${styles[toast.kind]}`}>
      <span>{toast.message}</span>
      <button
        type="button"
        className={styles.close}
        aria-label={t('toast.close')}
        onClick={() => onClose(toast.id)}
      >
        ×
      </button>
    </div>
  );
}
