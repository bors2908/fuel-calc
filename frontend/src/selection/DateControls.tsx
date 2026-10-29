import { useId } from "react";
import type { PriceSelection } from "./types";
import { presetRange } from "./dates";

type DateProps = {
  label: string;
  value: string;
  max: string;
  min?: string;
  errorId?: string;
  onChange: (value: string) => void;
};
export function DateField({ label, value, max, min, errorId, onChange }: DateProps) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type="date"
        required
        max={max}
        min={min}
        value={value}
        aria-invalid={errorId ? true : undefined}
        aria-describedby={errorId}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}
export function PeriodPreset({
  value,
  serverDate,
  onChange,
}: {
  value: PriceSelection;
  serverDate: string;
  onChange: (value: PriceSelection) => void;
}) {
  const id = useId();
  const preset = [1, 3, 12].find((months) => {
    const range = presetRange(serverDate, months);
    return range.dateFrom === value.dateFrom && range.dateTo === value.dateTo;
  });
  return (
    <div>
      <label htmlFor={id}>Период</label>
      <select
        id={id}
        value={preset?.toString() ?? "custom"}
        onChange={(event) =>
          onChange({ ...value, ...presetRange(serverDate, Number(event.target.value)) })
        }
      >
        <option
          value="custom"
          disabled
        >
          Свой диапазон
        </option>
        <option value="1">1 месяц</option>
        <option value="3">3 месяца</option>
        <option value="12">Год</option>
      </select>
    </div>
  );
}
export function DateRangeFields({
  value,
  serverDate,
  errorId,
  onChange,
}: {
  value: PriceSelection;
  serverDate: string;
  errorId: string | undefined;
  onChange: (value: PriceSelection) => void;
}) {
  return (
    <div className="date-range">
      <DateField
        label="С даты"
        value={value.dateFrom}
        max={serverDate}
        {...(errorId ? { errorId } : {})}
        onChange={(dateFrom) => onChange({ ...value, dateFrom })}
      />
      <span aria-hidden="true">—</span>
      <DateField
        label="По дату"
        value={value.dateTo}
        min={value.dateFrom}
        max={serverDate}
        {...(errorId ? { errorId } : {})}
        onChange={(dateTo) => onChange({ ...value, dateTo })}
      />
    </div>
  );
}
