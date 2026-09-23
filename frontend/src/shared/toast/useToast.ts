import { createContext, useContext } from 'react';

export type ToastKind = 'info' | 'success' | 'error';

export type ToastApi = {
  show: (message: string, kind?: ToastKind) => void;
};

export const ToastContext = createContext<ToastApi | null>(null);

export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside <ToastProvider>');
  }
  return context;
}
