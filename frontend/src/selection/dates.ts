import type { PriceSelection } from "./types";

export function forecastLimit(serverDate: string): string {
  const date = new Date(`${serverDate}T00:00:00Z`);
  const month = date.getUTCMonth();
  date.setUTCFullYear(date.getUTCFullYear() + 1);
  if (date.getUTCMonth() !== month) {
    date.setUTCDate(0);
  }
  return date.toISOString().slice(0, 10);
}

export function presetRange(
  serverDate: string,
  months: number,
): Pick<PriceSelection, "dateFrom" | "dateTo"> {
  const end = new Date(`${serverDate}T00:00:00Z`);
  const from = new Date(end);
  from.setUTCDate(1);
  from.setUTCMonth(from.getUTCMonth() - months);
  const lastDay = new Date(Date.UTC(from.getUTCFullYear(), from.getUTCMonth() + 1, 0)).getUTCDate();
  from.setUTCDate(Math.min(end.getUTCDate(), lastDay));
  return { dateFrom: from.toISOString().slice(0, 10), dateTo: serverDate };
}

export function validDate(value: string): boolean {
  return (
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    !Number.isNaN(Date.parse(`${value}T00:00:00Z`)) &&
    new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
  );
}
