import { useCallback, useState } from "react";
import { api, ApiError, errorMessage } from "../api/client";
import { defaultSorting } from "../api/types";
import type { Schema, Selection, Sorting } from "../api/types";
import { fromContext } from "../selection/model";
import { useRequestGate } from "../ui/useRequestGate";
import { checkContext, loadPage } from "./load";
import type { PricePage } from "./load";

export function usePrices() {
  const [page, setPage] = useState<PricePage | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [calculation, setCalculation] = useState<Schema<"RefuelingCalculation"> | null>(null);
  const [calculationError, setCalculationError] = useState<string | null>(null);
  const [calculating, setCalculating] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const { begin, cancel } = useRequestGate();
  const { begin: beginCalculation, cancel: cancelCalculation } = useRequestGate();

  const invalidateCalculation = useCallback(() => {
    cancelCalculation();
    setCalculation(null);
    setCalculationError(null);
    setCalculating(false);
  }, [cancelCalculation]);
  const load = useCallback(
    async (
      selection: Selection,
      options: { sorting?: Sorting; notice?: string; retryConflict?: boolean } = {},
    ) => {
      invalidateCalculation();
      setNotice(options.notice ?? null);
      const task = begin();
      setBusy(true);
      setError(null);
      try {
        const result = await loadPage(
          selection,
          options.sorting ?? defaultSorting,
          task.signal,
          options.retryConflict ?? true,
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
    [begin, invalidateCalculation],
  );
  const clear = useCallback(() => {
    cancel();
    invalidateCalculation();
    setPage(null);
    setError(null);
    setNotice(null);
    setBusy(false);
  }, [cancel, invalidateCalculation]);
  async function sort(sorting: Sorting) {
    if (!page) {
      return;
    }
    const selection = fromContext(page.overview.context);
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
        setPage({ ...page, comparison: { value: comparison } });
      }
    } catch (failure) {
      if (task.isCurrent()) {
        if (failure instanceof ApiError && failure.status === 409) {
          await load(selection, { sorting, retryConflict: false });
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
  async function calculate(volume: string) {
    if (!page) {
      return;
    }
    const selection = fromContext(page.overview.context);
    const task = beginCalculation();
    setCalculating(true);
    setCalculation(null);
    setCalculationError(null);
    setNotice(null);
    try {
      const snapshot = page.comparison.value?.comparison.snapshotId;
      const result = await api.calculate(
        { selection, volumeLiters: volume, ...(snapshot ? { expectedSnapshotId: snapshot } : {}) },
        task.signal,
      );
      checkContext(selection, result);
      // The POST owns its comparison. Synchronize the other blocks against that same revision.
      const version = result.meta.dataVersion;
      let synced = page;
      if (page.overview.meta.dataVersion !== version) {
        const [overview, trend] = await Promise.all([
          api.overview(selection, task.signal, version),
          api.trend(selection, task.signal, version),
        ]);
        checkContext(selection, overview, version);
        checkContext(selection, trend, version);
        synced = { ...page, overview, trend: { value: trend } };
      }
      const comparison: Schema<"PriceComparison"> = {
        meta: result.meta,
        context: result.context,
        sourceUpdates: result.sourceUpdates,
        comparison: {
          ...result.comparison,
          data: result.comparison.data.map((row) => ({ ...row, isBase: row.relation === "BASE" })),
        },
      };
      if (task.isCurrent()) {
        setPage({ ...synced, comparison: { value: comparison } });
        setCalculation(result);
      }
    } catch (failure) {
      if (task.isCurrent()) {
        if (failure instanceof ApiError && failure.status === 409) {
          await load(selection, {
            notice: "Цены обновились во время расчёта. Проверьте данные и рассчитайте ещё раз.",
            retryConflict: false,
          });
        } else {
          setCalculationError(errorMessage(failure));
        }
      }
    } finally {
      if (task.isCurrent()) {
        setCalculating(false);
      }
    }
  }
  return {
    page,
    busy,
    error,
    calculation,
    calculating,
    calculationError,
    notice,
    load,
    clear,
    sort,
    calculate,
    invalidateCalculation,
  };
}
