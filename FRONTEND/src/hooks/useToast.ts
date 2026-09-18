/**
 * Toast Notification Hook
 * Provides toast notifications for success/error states
 */

import { useState, useCallback } from "react";

export type ToastSeverity = "success" | "error" | "warning" | "info";

export interface ToastState {
  open: boolean;
  message: string;
  severity: ToastSeverity;
  autoHideDuration?: number;
}

export interface UseToastReturn {
  toast: ToastState;
  showToast: (message: string, severity: ToastSeverity, autoHideDuration?: number) => void;
  showSuccess: (message: string, autoHideDuration?: number) => void;
  showError: (message: string, autoHideDuration?: number) => void;
  showWarning: (message: string, autoHideDuration?: number) => void;
  showInfo: (message: string, autoHideDuration?: number) => void;
  hideToast: () => void;
}

export const useToast = (): UseToastReturn => {
  const [toast, setToast] = useState<ToastState>({
    open: false,
    message: "",
    severity: "info",
    autoHideDuration: 6000,
  });

  const showToast = useCallback(
    (message: string, severity: ToastSeverity, autoHideDuration = 6000) => {
      setToast({
        open: true,
        message,
        severity,
        autoHideDuration,
      });
    },
    []
  );

  const showSuccess = useCallback(
    (message: string, autoHideDuration?: number) => {
      showToast(message, "success", autoHideDuration);
    },
    [showToast]
  );

  const showError = useCallback(
    (message: string, autoHideDuration?: number) => {
      showToast(message, "error", autoHideDuration);
    },
    [showToast]
  );

  const showWarning = useCallback(
    (message: string, autoHideDuration?: number) => {
      showToast(message, "warning", autoHideDuration);
    },
    [showToast]
  );

  const showInfo = useCallback(
    (message: string, autoHideDuration?: number) => {
      showToast(message, "info", autoHideDuration);
    },
    [showToast]
  );

  const hideToast = useCallback(() => {
    setToast((prev) => ({ ...prev, open: false }));
  }, []);

  return {
    toast,
    showToast,
    showSuccess,
    showError,
    showWarning,
    showInfo,
    hideToast,
  };
};
