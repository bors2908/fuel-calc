import type { Sorting } from "../api/types";
import type { Source } from "../sources/model";

export type PriceSorting = Sorting & { source: Source };
export const defaultPriceSorting: PriceSorting = {
  source: "OFFICIAL",
  sortBy: "PRICE",
  sortOrder: "ASC",
};
