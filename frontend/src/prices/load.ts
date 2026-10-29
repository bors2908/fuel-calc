import { validatePriceRows } from "./validate";
import { api } from "../api/client";
import type { Schema } from "../api/types";
import type { PriceSelection } from "../selection/types";
import { checkContext } from "../selection/context";
import type { PriceSorting } from "./sorting";

export type PricePage = Schema<"PricePage"> & { selection: PriceSelection };
export async function loadPage(
  selection: PriceSelection,
  signal: AbortSignal,
  sorting: PriceSorting,
): Promise<PricePage> {
  const response = await api.prices(selection, signal, sorting);
  checkContext(selection, response.context);
  validatePriceRows(response);
  return { ...response, selection };
}
