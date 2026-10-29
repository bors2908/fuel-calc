import { useCallback, useState } from "react";
import { api, ApiError, errorMessage } from "../api/client";
import { defaultSorting } from "../api/types";
import type { Schema, Selection, Sorting } from "../api/types";
import { useRequestGate } from "../ui/useRequestGate";
import { checkContext, loadPage } from "./load";
import type { PricePage } from "./load";

export function usePrices() {
  const [page, setPage] = useState<PricePage | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { begin, cancel } = useRequestGate();
  const load = useCallback(
    async (
      selection: Selection,
      options: {
        sorting?: Sorting;
        officialSorting?: Sorting;
        ordering?: Schema<"PriceSource">;
        retryConflict?: boolean;
      } = {},
    ) => {
      const task = begin();
      setBusy(true);
      setError(null);
      try {
        const result = await loadPage(
          selection,
          options.sorting ?? defaultSorting,
          task.signal,
          options.retryConflict ?? true,
          undefined,
          options.officialSorting,
          options.ordering,
        );
        if (task.isCurrent()) {
          setPage(result);
        }
      } catch (failure) {
        if (task.isCurrent()) {
          setError(errorMessage(failure));
        }
      } finally {
        if (task.isCurrent()) {
          setBusy(false);
        }
      }
    },
    [begin],
  );
  const cancelLoad = useCallback(() => {
    cancel();
    setError(null);
    setBusy(false);
  }, [cancel]);
  const clear = useCallback(() => {
    cancelLoad();
    setPage(null);
  }, [cancelLoad]);
  async function sort(source: Schema<"PriceSource">, sorting: Sorting) {
    if (!page) {
      return;
    }
    const selection: Selection = {
      ...page.selection,
      source,
      frequency: source === "OFFICIAL" ? "WEEKLY" : "DAILY",
    };
    const task = begin();
    setBusy(true);
    setError(null);
    try {
      const comparison = await api.comparison(
        selection,
        sorting,
        task.signal,
        page.overview.meta.dataVersion,
      );
      checkContext(selection, comparison, page.overview.meta.dataVersion);
      if (task.isCurrent()) {
        setPage(
          source === "OFFICIAL"
            ? { ...page, comparisonOrder: source, officialComparison: { value: comparison } }
            : { ...page, comparisonOrder: source, comparison: { value: comparison } },
        );
      }
    } catch (failure) {
      if (task.isCurrent()) {
        if (failure instanceof ApiError && failure.status === 409) {
          await load(page.selection, {
            sorting:
              source === "MARKET" ? sorting : (page.comparison.value?.comparison ?? defaultSorting),
            officialSorting:
              source === "OFFICIAL"
                ? sorting
                : (page.officialComparison.value?.comparison ?? defaultSorting),
            retryConflict: false,
            ordering: source,
          });
        } else {
          setError(errorMessage(failure));
        }
      }
    } finally {
      if (task.isCurrent()) {
        setBusy(false);
      }
    }
  }
  return {
    page,
    busy,
    error,
    load,
    cancelLoad,
    clear,
    sort,
  };
}
