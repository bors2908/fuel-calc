import type { components } from "./generated/contract";

export type Schema<Name extends keyof components["schemas"]> = components["schemas"][Name];
export type PriceCell = Schema<"PriceCell">;
export type Sorting = { sortBy: Schema<"ComparisonSortBy">; sortOrder: Schema<"SortOrder"> };
