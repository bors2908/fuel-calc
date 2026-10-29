import type { PriceContext, Schema, Selection } from "../api/types";

export type Catalog = {
  regions: Schema<"RegionRef">[];
  fuels: Schema<"FuelTypeRef">[];
  serverDate: string;
};
export type DateMode = "range" | "date";
const defaultPeriodMonths = 3;
export function presetRange(
  serverDate: string,
  months: number,
): Pick<Selection, "dateFrom" | "dateTo"> {
  const end = new Date(`${serverDate}T00:00:00Z`);
  const from = new Date(end);
  from.setUTCDate(1);
  from.setUTCMonth(from.getUTCMonth() - months);
  const lastDay = new Date(Date.UTC(from.getUTCFullYear(), from.getUTCMonth() + 1, 0)).getUTCDate();
  from.setUTCDate(Math.min(end.getUTCDate(), lastDay));
  return { dateFrom: from.toISOString().slice(0, 10), dateTo: serverDate };
}
export function defaults(serverDate: string): Selection {
  return {
    baseRegionCode: "",
    regionCodes: [],
    fuelType: "DIESEL",
    frequency: "DAILY",
    source: "MARKET",
    ...presetRange(serverDate, defaultPeriodMonths),
  };
}
export function withDefaultRange(value: Selection): Selection {
  return validDate(value.dateTo)
    ? { ...value, ...presetRange(value.dateTo, defaultPeriodMonths) }
    : value;
}
export function selectionKey(value: Selection): string {
  return JSON.stringify([
    value.baseRegionCode,
    [...value.regionCodes].sort(),
    value.fuelType,
    value.frequency,
    value.source,
    value.dateFrom,
    value.dateTo,
  ]);
}
export function fromContext(value: PriceContext): Selection {
  return {
    baseRegionCode: value.baseRegion.okatoCode,
    regionCodes: value.regions.map((r) => r.okatoCode),
    fuelType: value.fuelType.code,
    frequency: value.frequency,
    source: value.source,
    dateFrom: value.dateFrom,
    dateTo: value.dateTo,
  };
}
function validDate(value: string): boolean {
  return (
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    !Number.isNaN(Date.parse(`${value}T00:00:00Z`)) &&
    new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
  );
}
export function selectionError(
  value: Selection,
  catalog: Catalog,
  dateMode: DateMode = "range",
): string | null {
  if (!value.baseRegionCode || !value.regionCodes.includes(value.baseRegionCode)) {
    return "Выберите основной регион.";
  }
  if (
    value.regionCodes.length > 4 ||
    new Set(value.regionCodes).size !== value.regionCodes.length
  ) {
    return "Выберите не более четырёх разных регионов.";
  }
  if (
    value.regionCodes.some((code) => !catalog.regions.some((region) => region.okatoCode === code))
  ) {
    return "Регион из ссылки не найден. Выберите регион из списка.";
  }
  if (!catalog.fuels.some((fuel) => fuel.code === value.fuelType)) {
    return "Выберите вид топлива из списка.";
  }
  if (
    !validDate(value.dateFrom) ||
    !validDate(value.dateTo) ||
    value.dateFrom > value.dateTo ||
    value.dateTo > catalog.serverDate
  ) {
    return dateMode === "date"
      ? "Укажите корректную дату не позднее сегодняшней."
      : "Укажите корректный период без будущих дат.";
  }
  return null;
}
export function readSelection(
  catalog: Catalog,
  dateMode: DateMode = "range",
): {
  selection: Selection;
  requested: boolean;
  error: string | null;
} {
  const query = new URLSearchParams(location.search);
  const value = defaults(catalog.serverDate);
  const requested = query.has("region");
  value.baseRegionCode = query.get("region") ?? "";
  value.regionCodes =
    query.get("regions")?.split(",") ?? (value.baseRegionCode ? [value.baseRegionCode] : []);
  value.dateFrom = query.get("from") ?? value.dateFrom;
  value.dateTo = query.get("to") ?? value.dateTo;
  if (dateMode === "date") {
    value.dateTo = query.get("date") ?? query.get("to") ?? catalog.serverDate;
    value.dateFrom = value.dateTo;
  }
  const fuel = query.get("fuel");
  const invalidEnum = fuel !== null && !catalog.fuels.some((item) => item.code === fuel);
  if (fuel === "DIESEL" || fuel === "AI_92" || fuel === "AI_95") {
    value.fuelType = fuel;
  }
  return {
    selection: value,
    requested,
    error: requested
      ? invalidEnum
        ? "Параметры ссылки не распознаны. Проверьте выбор."
        : selectionError(value, catalog, dateMode)
      : null,
  };
}
export function selectionHref(value: Selection, path: string): string {
  const query = new URLSearchParams({
    ...(value.baseRegionCode
      ? { region: value.baseRegionCode, regions: value.regionCodes.join(",") }
      : {}),
    fuel: value.fuelType,
    ...(path.replace(/\/$/, "") === "/refueling"
      ? { date: value.dateTo }
      : { from: value.dateFrom, to: value.dateTo }),
  });
  return `${path}?${query}`;
}
export function writeSelection(value: Selection): void {
  history.pushState(null, "", `${selectionHref(value, location.pathname)}${location.hash}`);
}
