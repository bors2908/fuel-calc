import type { ReactNode } from "react";

export function ObservationButton({
  id,
  onObservation,
  children = "Об источнике и дате",
  className = "text-button",
  label,
}: {
  id: string;
  onObservation: (id: string) => void;
  children?: ReactNode;
  className?: string;
  label?: string;
}) {
  return (
    <button
      type="button"
      className={className}
      aria-label={label}
      onClick={() => onObservation(id)}
    >
      {children}
    </button>
  );
}
