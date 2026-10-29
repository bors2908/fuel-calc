import Ajv from "ajv";
import addFormats from "ajv-formats";
import definitions from "./generated/schemas.json" with { type: "json" };
import type { Schema } from "./types";
import type { PriceSelection } from "../selection/types";
import type { PriceSorting } from "../prices/sorting";

const validator = new Ajv({ strict: false });
addFormats(validator);
validator.addSchema({ $id: "fuel", ...definitions });
const schemaReferences = new Map(
  Object.keys(definitions.definitions).map((name) => [name, { $ref: `fuel#/definitions/${name}` }]),
);

export function decode<Name extends keyof typeof definitions.definitions>(
  name: Name,
  value: unknown,
): Schema<Name> {
  const reference = schemaReferences.get(name);
  if (!reference) {
    throw new Error("Response schema is missing.");
  }
  const validate = validator.compile<Schema<Name>>(reference);
  if (!validate(value)) {
    throw new Error("Не удалось прочитать ответ сервиса. Попробуйте обновить данные.");
  }
  return value;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly response: Schema<"ErrorResponse">,
  ) {
    super(response.message);
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 400) {
      return "Проверьте регионы, даты и объём. Выбор не принят сервисом.";
    }
    if (error.status === 404) {
      return "Сведения об этой цене не найдены.";
    }
  }
  return error instanceof TypeError
    ? "Нет соединения с сервисом. Проверьте подключение и повторите запрос."
    : "Не удалось загрузить данные. Попробуйте ещё раз.";
}

async function request<Name extends keyof typeof definitions.definitions>(
  name: Name,
  path: string,
  signal: AbortSignal,
  body?: Schema<"RefuelingCalculateRequest">,
): Promise<Schema<Name>> {
  const response = await fetch(`/api/v1/${path}`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(30_000)]),
    cache: "no-store",
    headers: {
      Accept: "application/json",
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    ...(body ? { method: "POST", body: JSON.stringify(body) } : {}),
  });
  const value: unknown = await response.json();
  if (!response.ok) {
    throw new ApiError(response.status, decode("ErrorResponse", value));
  }
  return decode(name, value);
}

export function selectionQuery(selection: PriceSelection): URLSearchParams {
  const query = new URLSearchParams({
    baseRegionCode: selection.baseRegionCode,
    filterRegionCode: selection.regionCodes.join(","),
    filterFuelType: selection.fuelType,
    filterDateFrom: selection.dateFrom,
    filterDateTo: selection.dateTo,
  });
  return query;
}

export const api = {
  regions: (signal: AbortSignal) => request("RegionList", "regions", signal),
  fuels: (signal: AbortSignal) => request("FuelTypeList", "fuel-types", signal),
  prices: (selection: PriceSelection, signal: AbortSignal, sorting: PriceSorting) => {
    const query = selectionQuery(selection);
    query.set("sortSource", sorting.source);
    query.set("sortBy", sorting.sortBy);
    query.set("sortOrder", sorting.sortOrder);
    return request("PricePage", `fuel-prices?${query}`, signal);
  },
  observation: (id: string, signal: AbortSignal) =>
    request(
      "PriceObservationResponse",
      `fuel-prices/observations/${encodeURIComponent(id)}`,
      signal,
    ),
  calculate: (body: Schema<"RefuelingCalculateRequest">, signal: AbortSignal) =>
    request("RefuelingCalculation", "refueling/calculate", signal, body),
};
