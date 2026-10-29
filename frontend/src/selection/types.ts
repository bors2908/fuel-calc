import type { Schema } from "../api/types";

export type Catalog = {
  regions: Schema<"RegionRef">[];
  fuels: Schema<"FuelTypeRef">[];
  serverDate: string;
};
export type BaseSelection = {
  baseRegionCode: string;
  regionCodes: string[];
  fuelType: Schema<"FuelType">;
};
export type PriceSelection = BaseSelection & { dateFrom: string; dateTo: string };
export type RefuelingSelection = BaseSelection & { date: string };
export type Selection = PriceSelection | RefuelingSelection;
export type ParsedSelection<S> = { selection: S; requested: boolean; error: string | null };
export type SelectionModel<S> = {
  parse: (query: URLSearchParams, catalog: Catalog) => ParsedSelection<S>;
  validate: (value: S, catalog: Catalog) => string | null;
};
