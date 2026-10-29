import type { components } from "./generated/contract";

export type Schema<Name extends keyof components["schemas"]> = components["schemas"][Name];
export type Selection = Schema<"PriceSelection">;
export type PriceCell = Schema<"PriceCell">;
export type PriceContext = Schema<"PriceContext">;
export type Sorting = { sortBy: Schema<"ComparisonSortBy">; sortOrder: Schema<"SortOrder"> };

export const defaultSorting: Sorting = { sortBy: "PRICE", sortOrder: "ASC" };
export const sourceNames = { OFFICIAL: "Официальная цена", MARKET: "Рыночная цена" };
