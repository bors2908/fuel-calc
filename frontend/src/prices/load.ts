import { api, ApiError } from "../api/client";
import type { Schema, Selection, Sorting } from "../api/types";
import { fromContext, selectionKey } from "../selection/model";

export type Block<T> = { value: T; error?: never } | { value?: never; error: unknown };
export type PricePage = {
  overview: Schema<"PriceOverview">;
  trend: Block<Schema<"PriceTrend">>;
  comparison: Block<Schema<"PriceComparison">>;
};
export function checkContext(
  selection: Selection,
  response: { context: Schema<"PriceContext">; meta: Schema<"ApiMeta"> },
  version?: string,
) {
  if (
    selectionKey(fromContext(response.context)) !== selectionKey(selection) ||
    (version !== undefined && response.meta.dataVersion !== version)
  ) {
    throw new Error("Inconsistent response context or revision.");
  }
}
function block<T>(result: PromiseSettledResult<T>): Block<T> {
  if (result.status === "fulfilled") {
    return { value: result.value };
  }
  const error: unknown = result.reason;
  if (error instanceof ApiError && error.status === 409) {
    throw error;
  }
  return { error };
}
export async function loadPage(
  selection: Selection,
  sorting: Sorting,
  signal: AbortSignal,
  retryConflict = true,
): Promise<PricePage> {
  for (let attempt = 0; attempt < (retryConflict ? 2 : 1); attempt++) {
    try {
      const overview = await api.overview(selection, signal);
      checkContext(selection, overview);
      const version = overview.meta.dataVersion;
      const attempt = new AbortController();
      const attemptSignal = AbortSignal.any([signal, attempt.signal]);
      const cancelOnConflict = (error: unknown): never => {
        if (error instanceof ApiError && error.status === 409) {
          attempt.abort();
        }
        throw error;
      };
      const [trendResult, comparisonResult] = await Promise.allSettled([
        api.trend(selection, attemptSignal, version).catch(cancelOnConflict),
        api.comparison(selection, sorting, attemptSignal, version).catch(cancelOnConflict),
      ]);
      const trend = block(trendResult);
      const comparison = block(comparisonResult);
      if (trend.value) {
        checkContext(selection, trend.value, version);
      }
      if (comparison.value) {
        checkContext(selection, comparison.value, version);
      }
      return { overview, trend, comparison };
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 409 && attempt === 0 && retryConflict)) {
        throw error;
      }
    }
  }
  throw new Error("Revision retry exhausted.");
}
