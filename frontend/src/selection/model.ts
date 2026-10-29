import { presetRange, validDate } from "./dates";
import type {
  BaseSelection,
  Catalog,
  ParsedSelection,
  PriceSelection,
  RefuelingSelection,
  Selection,
  SelectionModel,
} from "./types";

const defaultPeriodMonths = 3;
const emptyBase: BaseSelection = { baseRegionCode: "", regionCodes: [], fuelType: "DIESEL" };
export function priceDefaults(serverDate: string): PriceSelection {
  return { ...emptyBase, ...presetRange(serverDate, defaultPeriodMonths) };
}
export function refuelingDefaults(serverDate: string): RefuelingSelection {
  return { ...emptyBase, date: serverDate };
}
export function selectionKey(value: PriceSelection): string {
  return JSON.stringify([
    value.baseRegionCode,
    [...value.regionCodes].sort(),
    value.fuelType,
    value.dateFrom,
    value.dateTo,
  ]);
}
export function baseSelectionError(value: BaseSelection, catalog: Catalog): string | null {
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
  return null;
}
function readBase(query: URLSearchParams): BaseSelection {
  const baseRegionCode = query.get("region") ?? "";
  const fuel = query.get("fuel");
  return {
    baseRegionCode,
    regionCodes: query.get("regions")?.split(",") ?? (baseRegionCode ? [baseRegionCode] : []),
    fuelType: fuel === "AI_92" || fuel === "AI_95" ? fuel : "DIESEL",
  };
}
function parsed<S extends BaseSelection>(
  query: URLSearchParams,
  selection: S,
  catalog: Catalog,
  validate: SelectionModel<S>["validate"],
): ParsedSelection<S> {
  const requested = query.has("region");
  const fuel = query.get("fuel");
  const unknownFuel = fuel !== null && !catalog.fuels.some((item) => item.code === fuel);
  return {
    selection,
    requested,
    error: requested
      ? unknownFuel
        ? "Параметры ссылки не распознаны. Проверьте выбор."
        : validate(selection, catalog)
      : null,
  };
}
export const priceSelectionModel: SelectionModel<PriceSelection> = {
  validate(value, catalog) {
    return (
      baseSelectionError(value, catalog) ??
      (!validDate(value.dateFrom) ||
      !validDate(value.dateTo) ||
      value.dateFrom > value.dateTo ||
      value.dateTo > catalog.serverDate
        ? "Укажите корректный период без будущих дат."
        : null)
    );
  },
  parse(query, catalog) {
    const defaults = priceDefaults(catalog.serverDate);
    const selection = {
      ...readBase(query),
      dateFrom: query.get("from") ?? defaults.dateFrom,
      dateTo: query.get("to") ?? defaults.dateTo,
    };
    return parsed(query, selection, catalog, this.validate);
  },
};
export const refuelingSelectionModel: SelectionModel<RefuelingSelection> = {
  validate(value, catalog) {
    return (
      baseSelectionError(value, catalog) ??
      (!validDate(value.date) || value.date > catalog.serverDate
        ? "Укажите корректную дату не позднее сегодняшней."
        : null)
    );
  },
  parse(query, catalog) {
    const selection = {
      ...readBase(query),
      date: query.get("date") ?? query.get("to") ?? catalog.serverDate,
    };
    return parsed(query, selection, catalog, this.validate);
  },
};
export function selectionHref(value: Selection, path: string): string {
  const end = "date" in value ? value.date : value.dateTo;
  const range =
    "date" in value
      ? validDate(value.date)
        ? presetRange(value.date, defaultPeriodMonths)
        : { dateFrom: value.date, dateTo: value.date }
      : value;
  const query = new URLSearchParams({
    ...(value.baseRegionCode
      ? { region: value.baseRegionCode, regions: value.regionCodes.join(",") }
      : {}),
    fuel: value.fuelType,
    ...(path.replace(/\/$/, "") === "/refueling"
      ? { date: end }
      : { from: range.dateFrom, to: range.dateTo }),
  });
  return `${path}?${query}`;
}
