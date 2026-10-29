import { api } from "../api/client";
import type { Schema } from "../api/types";
import type { RefuelingSelection } from "../selection/types";
import { checkContext } from "../selection/context";
import { useAsyncRequest } from "../ui/useAsyncRequest";
import { sources } from "../sources/model";

export function useRefueling() {
  const {
    value: result,
    busy,
    error,
    run,
    clear,
  } = useAsyncRequest<Schema<"RefuelingCalculation">>({ keepPrevious: false });
  async function calculate(selection: RefuelingSelection, volume: string) {
    await run(async (signal) => {
      const value = await api.calculate({ selection, volumeLiters: volume }, signal);
      if (
        value.results.length !== sources.length ||
        !sources.every((source, index) => value.results[index]?.source === source)
      ) {
        throw new Error("Invalid calculation sources");
      }
      checkContext(selection, value.context);
      return value;
    });
  }
  return { result, busy, error, calculate, invalidate: clear };
}
