import { api, ApiError } from "../api/client";
import type { Schema, Selection, Sorting } from "../api/types";
import { fromContext, selectionKey } from "../selection/model";

export type Block<T> =
  | { value: T; error?: never }
  | { value?: never; error: unknown }
  | { value?: never; error?: never; skipped: true };
export type PricePage = {
  overview: Schema<"PriceOverview">;
  selection: Selection;
  comparisonOrder: Schema<"PriceSource">;
  marketOverview: Block<Schema<"PriceOverview">>;
  officialOverview: Block<Schema<"PriceOverview">>;
  trend: Block<Schema<"PriceTrend">>;
  officialTrend: Block<Schema<"PriceTrend">>;
  comparison: Block<Schema<"PriceComparison">>;
  officialComparison: Block<Schema<"PriceComparison">>;
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
function block<T>(result: PromiseSettledResult<T | null>): Block<T> {
  if (result.status === "fulfilled") {
    return result.value === null ? { skipped: true } : { value: result.value };
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
  fixedVersion?: string,
  officialSorting: Sorting = { sortBy: "PRICE", sortOrder: "ASC" },
  ordering?: Schema<"PriceSource">,
): Promise<PricePage> {
  for (let attempt = 0; attempt < (retryConflict ? 2 : 1); attempt++) {
    try {
      const officialSelection: Selection = {
        ...selection,
        source: "OFFICIAL",
        frequency: "WEEKLY",
      };
      let overview: Schema<"PriceOverview">;
      try {
        overview = await api.overview(selection, signal, fixedVersion);
        checkContext(selection, overview, fixedVersion);
      } catch (error) {
        if (signal.aborted || (error instanceof ApiError && error.status === 409)) {
          throw error;
        }
        overview = await api.overview(officialSelection, signal, fixedVersion);
        checkContext(officialSelection, overview, fixedVersion);
      }
      const version = overview.meta.dataVersion;
      const attempt = new AbortController();
      const attemptSignal = AbortSignal.any([signal, attempt.signal]);
      const cancelOnConflict = (error: unknown): never => {
        if (error instanceof ApiError && error.status === 409) {
          attempt.abort();
        }
        throw error;
      };
      const [
        trendResult,
        officialResult,
        comparisonResult,
        officialOverviewResult,
        officialComparisonResult,
        marketOverviewResult,
      ] = await Promise.allSettled([
        api.trend(selection, attemptSignal, version).catch(cancelOnConflict),
        api.trend(officialSelection, attemptSignal, version).catch(cancelOnConflict),
        selection.regionCodes.length > 1
          ? api.comparison(selection, sorting, attemptSignal, version).catch(cancelOnConflict)
          : Promise.resolve(null),
        (overview.context.source === "OFFICIAL"
          ? Promise.resolve(overview)
          : api.overview(officialSelection, attemptSignal, version)
        ).catch(cancelOnConflict),
        selection.regionCodes.length > 1
          ? api
              .comparison(officialSelection, officialSorting, attemptSignal, version)
              .catch(cancelOnConflict)
          : Promise.resolve(null),
        (overview.context.source === "MARKET"
          ? Promise.resolve(overview)
          : api.overview(selection, attemptSignal, version)
        ).catch(cancelOnConflict),
      ]);
      const marketOverview = block(marketOverviewResult);
      if (marketOverview.value) {
        checkContext(selection, marketOverview.value, version);
      }
      const officialComparison = block(officialComparisonResult);
      if (officialComparison.value) {
        checkContext(officialSelection, officialComparison.value, version);
      }
      const officialOverview = block(officialOverviewResult);
      if (officialOverview.value) {
        checkContext(officialSelection, officialOverview.value, version);
      }
      const trend = block(trendResult);
      const comparison = block(comparisonResult);
      const officialTrend = block(officialResult);
      if (officialTrend.value) {
        checkContext(officialSelection, officialTrend.value, version);
      }
      if (trend.value) {
        checkContext(selection, trend.value, version);
      }
      if (comparison.value) {
        checkContext(selection, comparison.value, version);
      }
      return {
        overview,
        comparisonOrder:
          ordering ??
          (officialComparison.value &&
          ["AVAILABLE", "SINGLE_REGION"].includes(officialComparison.value.comparison.status)
            ? "OFFICIAL"
            : "MARKET"),
        selection,
        marketOverview,
        officialOverview,
        trend,
        officialTrend,
        comparison,
        officialComparison,
      };
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 409 && attempt === 0 && retryConflict)) {
        throw error;
      }
    }
  }
  throw new Error("Revision retry exhausted.");
}
