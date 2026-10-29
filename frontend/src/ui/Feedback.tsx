import type { ReactNode } from "react";

export function Feedback({ children, onRetry }: { children: ReactNode; onRetry?: () => void }) {
  return (
    <div
      className="feedback"
      role="alert"
    >
      <p>{children}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
        >
          Повторить
        </button>
      )}
    </div>
  );
}
export function Loading({ children = "Загружаем данные…" }: { children?: ReactNode }) {
  return (
    <p
      className="loading"
      role="status"
    >
      {children}
    </p>
  );
}
