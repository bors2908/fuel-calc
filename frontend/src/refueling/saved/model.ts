import type { Schema } from "../../api/types";
import { decode } from "../../api/client";
import { sources } from "../../sources/model";
import type { Source } from "../../sources/model";
import { validDate } from "../../selection/dates";
import { normalizeVolume } from "../model";

export const savedLimit = 10;
export type SavedQuote = { price: Schema<"AvailablePriceCell">; totalRub: string };
export type SavedRefueling = {
  id: string;
  fuel: Schema<"FuelTypeRef">;
  requestedDate: string;
  volumeLiters: string;
  calculatedAt: string;
  savedAt: string;
  quotes: Record<Source, SavedQuote | null>;
};
export type SavedList = {
  version: 1;
  region: Schema<"RegionRef"> | null;
  entries: SavedRefueling[];
};
export function emptyList(): SavedList {
  return { version: 1, region: null, entries: [] };
}
export function minorUnits(value: string): bigint {
  const [integer = "0", fraction = ""] = value.split(".");
  return BigInt(integer) * 100n + BigInt(fraction.padEnd(2, "0"));
}
function decimal(value: bigint): string {
  return `${value / 100n}.${(value % 100n).toString().padStart(2, "0")}`;
}
export function savedTotals(entries: SavedRefueling[]) {
  const total = (source: Source) => {
    const quotes = entries.flatMap((entry) => (entry.quotes[source] ? [entry.quotes[source]] : []));
    return {
      amount: quotes.length
        ? decimal(quotes.reduce((sum, quote) => sum + minorUnits(quote.totalRub), 0n))
        : null,
      count: quotes.length,
    };
  };
  return {
    volumeLiters: decimal(entries.reduce((sum, entry) => sum + minorUnits(entry.volumeLiters), 0n)),
    OFFICIAL: total("OFFICIAL"),
    MARKET: total("MARKET"),
  };
}
export function snapshot(result: Schema<"RefuelingCalculation">): SavedRefueling {
  if (result.context.regions.length !== 1) {
    throw new Error("Выберите один регион для плана закупок топлива.");
  }
  function quote(source: Source): SavedQuote | null {
    const block = result.results.find((item) => item.source === source);
    const row = block?.comparison?.data.find(
      (item) => item.region.okatoCode === result.context.baseRegion.okatoCode,
    );
    return row ? { price: row.price, totalRub: row.totalRub } : null;
  }
  const quotes = { OFFICIAL: quote("OFFICIAL"), MARKET: quote("MARKET") };
  if (!sources.some((source) => quotes[source])) {
    throw new Error("Нет доступных цен для сохранения заправки.");
  }
  return structuredClone({
    id: crypto.randomUUID(),
    fuel: result.context.fuelType,
    requestedDate: result.context.dateTo,
    volumeLiters: result.volumeLiters,
    calculatedAt: result.meta.generatedAt,
    savedAt: new Date().toISOString(),
    quotes,
  });
}
function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Invalid saved record");
  }
  return value as Record<string, unknown>;
}
function text(value: unknown): string {
  if (typeof value !== "string" || value.length > 200) {
    throw new Error("Invalid saved text");
  }
  return value;
}
function instant(value: unknown): string {
  const result = text(value);
  if (!/^\d{4}-\d{2}-\d{2}T/.test(result) || !Number.isFinite(Date.parse(result))) {
    throw new Error("Invalid saved timestamp");
  }
  return result;
}
function readQuote(value: unknown, source: Source): SavedQuote | null {
  if (value === null) {
    return null;
  }
  const fields = record(value);
  const price = decode("AvailablePriceCell", fields.price);
  const totalRub = text(fields.totalRub);
  if (
    price.source !== source ||
    (price.observationId === null) !== Boolean(price.forecast) ||
    !/^\d{1,20}\.\d{2}$/.test(totalRub) ||
    !validDate(price.period.referenceDate)
  ) {
    throw new Error("Invalid saved price");
  }
  return { price, totalRub };
}
export function parseSavedList(value: unknown): SavedList {
  const fields = record(value);
  if (
    fields.version !== 1 ||
    !Array.isArray(fields.entries) ||
    fields.entries.length > savedLimit
  ) {
    throw new Error("Invalid saved list version or size");
  }
  const region = fields.region === null ? null : decode("RegionRef", fields.region);
  const entries: SavedRefueling[] = fields.entries.map((value: unknown) => {
    const item = record(value);
    const quotes = record(item.quotes);
    const volumeLiters = text(item.volumeLiters);
    const requestedDate = text(item.requestedDate);
    const id = text(item.id);
    if (!id || !validDate(requestedDate) || normalizeVolume(volumeLiters) === null) {
      throw new Error("Invalid saved refueling");
    }
    const result = {
      id,
      fuel: decode("FuelTypeRef", item.fuel),
      requestedDate,
      volumeLiters,
      calculatedAt: instant(item.calculatedAt),
      savedAt: instant(item.savedAt),
      quotes: {
        OFFICIAL: readQuote(quotes.OFFICIAL, "OFFICIAL"),
        MARKET: readQuote(quotes.MARKET, "MARKET"),
      },
    };
    if (!sources.some((source) => result.quotes[source])) {
      throw new Error("Saved refueling has no prices");
    }
    return result;
  });
  if (
    (entries.length === 0) !== (region === null) ||
    new Set(entries.map((entry) => entry.id)).size !== entries.length
  ) {
    throw new Error("Invalid saved region or identities");
  }
  return { version: 1, region, entries };
}
