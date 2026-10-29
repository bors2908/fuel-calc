import { useCallback, useState } from "react";
import { api, errorMessage } from "../api/client";
import type { Schema, Selection } from "../api/types";
import { checkContext } from "../prices/load";
import { useRequestGate } from "../ui/useRequestGate";

export function useRefueling() {
  const [result, setResult] = useState<Schema<"RefuelingCalculation"> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { begin, cancel } = useRequestGate();
  const invalidate = useCallback(() => {
    cancel();
    setResult(null);
    setError(null);
    setBusy(false);
  }, [cancel]);
  async function calculate(selection: Selection, volume: string) {
    const task = begin();
    setBusy(true);
    setResult(null);
    setError(null);
    try {
      const value = await api.calculate(
        {
          selection: {
            baseRegionCode: selection.baseRegionCode,
            regionCodes: selection.regionCodes,
            fuelType: selection.fuelType,
            date: selection.dateTo,
          },
          volumeLiters: volume,
        },
        task.signal,
      );
      if (
        value.results.length !== 2 ||
        value.results[0]?.context.source !== "OFFICIAL" ||
        value.results[1]?.context.source !== "MARKET"
      ) {
        throw new Error("Invalid calculation sources");
      }
      for (const item of value.results) {
        checkContext(
          {
            ...selection,
            source: item.context.source,
            frequency: item.context.source === "OFFICIAL" ? "WEEKLY" : "DAILY",
          },
          { context: item.context, meta: value.meta },
        );
      }
      if (task.isCurrent()) {
        setResult(value);
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
  }
  return { result, error, busy, invalidate, calculate };
}
