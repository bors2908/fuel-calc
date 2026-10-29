import { useCallback, useRef } from "react";
import type { PriceSelection } from "../selection/types";
import { useAsyncRequest } from "../ui/useAsyncRequest";
import { defaultPriceSorting } from "./sorting";
import type { PriceSorting } from "./sorting";
import { loadPage } from "./load";
import type { PricePage } from "./load";

export function usePrices() {
  const {
    value: page,
    busy,
    error,
    run,
    cancel,
    clear,
  } = useAsyncRequest<PricePage>({ keepPrevious: true });
  const ordering = useRef<PriceSorting>(defaultPriceSorting);
  const load = useCallback(
    async (selection: PriceSelection, sorting: PriceSorting = ordering.current) => {
      ordering.current = sorting;
      await run((signal) => loadPage(selection, signal, sorting));
    },
    [run],
  );
  async function sort(sorting: PriceSorting) {
    if (page) {
      await load(page.selection, sorting);
    }
  }
  return { page, busy, error, load, clear, cancelLoad: cancel, sort };
}
